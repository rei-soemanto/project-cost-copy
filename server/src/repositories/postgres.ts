import type { Pool, PoolClient } from "pg";
import { isUniqueViolation, toSafeInt, withTransaction } from "../db/pool.js";
import type { CostItem, CostItemKind, Project, User } from "../domain/types.js";
import {
  EmailTakenError,
  ProjectExistsError,
  type ConsumeResult,
  type NewProject,
  type NewUser,
  type ProjectHeaderPatch,
  type ProjectRepository,
  type RefreshTokenRepository,
  type UserRepository,
} from "./types.js";

type Queryable = Pick<Pool, "query"> | PoolClient;

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------

interface UserRow {
  id: string;
  email: string;
  full_name: string;
  password_hash: string;
  created_at: Date;
}

const toUser = (r: UserRow): User => ({
  id: r.id,
  email: r.email,
  fullName: r.full_name,
  passwordHash: r.password_hash,
  createdAt: r.created_at,
});

export class PgUserRepository implements UserRepository {
  constructor(private readonly pool: Pool) {}

  async findByEmail(email: string) {
    const { rows } = await this.pool.query<UserRow>("SELECT * FROM users WHERE lower(email) = lower($1)", [email]);
    return rows[0] ? toUser(rows[0]) : null;
  }

  async findById(id: string) {
    const { rows } = await this.pool.query<UserRow>("SELECT * FROM users WHERE id = $1", [id]);
    return rows[0] ? toUser(rows[0]) : null;
  }

  async findManyByIds(ids: string[]) {
    if (ids.length === 0) return [];
    const { rows } = await this.pool.query<UserRow>("SELECT * FROM users WHERE id = ANY($1::uuid[])", [ids]);
    return rows.map(toUser);
  }

  async create(input: NewUser) {
    try {
      const { rows } = await this.pool.query<UserRow>(
        "INSERT INTO users (email, full_name, password_hash) VALUES ($1, $2, $3) RETURNING *",
        [input.email, input.fullName, input.passwordHash],
      );
      return toUser(rows[0]!);
    } catch (error) {
      // Relying on the unique index rather than a prior SELECT closes the race
      // where two registrations for the same email arrive together.
      if (isUniqueViolation(error)) throw new EmailTakenError();
      throw error;
    }
  }
}

// ---------------------------------------------------------------------------
// Refresh tokens
// ---------------------------------------------------------------------------

export class PgRefreshTokenRepository implements RefreshTokenRepository {
  constructor(private readonly pool: Pool) {}

  async create(input: { userId: string; tokenHash: string; expiresAt: Date }) {
    await this.pool.query("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES ($1, $2, $3)", [
      input.userId,
      input.tokenHash,
      input.expiresAt,
    ]);
  }

  async consume(tokenHash: string, now: Date): Promise<ConsumeResult> {
    // The WHERE clause makes this atomic: of two concurrent refreshes with the
    // same token, only one UPDATE can match the still-unrevoked row.
    const consumed = await this.pool.query<{ user_id: string }>(
      `UPDATE refresh_tokens SET revoked_at = $2
        WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > $2
        RETURNING user_id`,
      [tokenHash, now],
    );
    if (consumed.rows[0]) return { status: "ok", userId: consumed.rows[0].user_id };

    // Not consumable. Distinguish "already used" (possible theft) from unknown/expired.
    const existing = await this.pool.query<{ user_id: string; revoked_at: Date | null }>(
      "SELECT user_id, revoked_at FROM refresh_tokens WHERE token_hash = $1",
      [tokenHash],
    );
    const row = existing.rows[0];
    if (row?.revoked_at) return { status: "reused", userId: row.user_id };
    return { status: "invalid" };
  }

  async revokeAllForUser(userId: string, now: Date) {
    await this.pool.query("UPDATE refresh_tokens SET revoked_at = $2 WHERE user_id = $1 AND revoked_at IS NULL", [
      userId,
      now,
    ]);
  }
}

// ---------------------------------------------------------------------------
// Projects
// ---------------------------------------------------------------------------

interface ProjectRow {
  id: string;
  owner_id: string;
  name: string;
  customer: string;
  pic: string;
  harga_kontrak: string;
  created_at: Date;
  updated_at: Date;
}

interface ItemRow {
  project_id: string;
  id: string;
  kind: CostItemKind;
  description: string;
  engineer: string | null;
  quantity: string | null;
  amount: string;
}

const toItem = (r: ItemRow): CostItem => ({
  id: r.id,
  kind: r.kind,
  description: r.description,
  engineer: r.engineer,
  quantity: r.quantity === null ? null : toSafeInt(r.quantity, "cost_items.quantity"),
  amount: toSafeInt(r.amount, "cost_items.amount"),
});

const toProject = (r: ProjectRow, items: CostItem[]): Project => ({
  id: r.id,
  ownerId: r.owner_id,
  name: r.name,
  customer: r.customer,
  pic: r.pic,
  hargaKontrak: toSafeInt(r.harga_kontrak, "projects.harga_kontrak"),
  items,
  createdAt: r.created_at,
  updatedAt: r.updated_at,
});

/**
 * Loads the items for many projects in a single query and groups them. Loading
 * per project inside a loop would be the classic N+1: one round trip per project.
 */
async function loadItems(db: Queryable, projectIds: string[]): Promise<Map<string, CostItem[]>> {
  const grouped = new Map<string, CostItem[]>(projectIds.map((id) => [id, []]));
  if (projectIds.length === 0) return grouped;

  const { rows } = await db.query<ItemRow>(
    "SELECT * FROM cost_items WHERE project_id = ANY($1) ORDER BY project_id, position",
    [projectIds],
  );
  for (const row of rows) grouped.get(row.project_id)?.push(toItem(row));
  return grouped;
}

async function insertItems(db: Queryable, projectId: string, items: CostItem[]) {
  if (items.length === 0) return;

  const COLUMNS = 8;
  const values: unknown[] = [];
  const tuples = items.map((item, position) => {
    values.push(projectId, item.id, position, item.kind, item.description, item.engineer, item.quantity, item.amount);
    const base = position * COLUMNS;
    const placeholders = Array.from({ length: COLUMNS }, (_, i) => `$${base + i + 1}`);
    return `(${placeholders.join(", ")})`;
  });

  await db.query(
    `INSERT INTO cost_items (project_id, id, position, kind, description, engineer, quantity, amount)
     VALUES ${tuples.join(", ")}`,
    values,
  );
}

export class PgProjectRepository implements ProjectRepository {
  constructor(private readonly pool: Pool) {}

  async listByOwner(ownerId: string) {
    const { rows } = await this.pool.query<ProjectRow>(
      "SELECT * FROM projects WHERE owner_id = $1 ORDER BY created_at DESC",
      [ownerId],
    );
    const items = await loadItems(this.pool, rows.map((r) => r.id));
    return rows.map((r) => toProject(r, items.get(r.id) ?? []));
  }

  async listAll() {
    const { rows } = await this.pool.query<ProjectRow>("SELECT * FROM projects ORDER BY created_at DESC");
    const items = await loadItems(this.pool, rows.map((r) => r.id));
    return rows.map((r) => toProject(r, items.get(r.id) ?? []));
  }

  async findById(ownerId: string, id: string) {
    return this.findOwned(this.pool, ownerId, id);
  }

  async create(input: NewProject) {
    try {
      return await withTransaction(this.pool, async (client) => {
        await client.query(
          `INSERT INTO projects (id, owner_id, name, customer, pic, harga_kontrak)
           VALUES ($1, $2, $3, $4, $5, $6)`,
          [input.id, input.ownerId, input.name, input.customer, input.pic, input.hargaKontrak],
        );
        await insertItems(client, input.id, input.items);
        return (await this.findOwned(client, input.ownerId, input.id))!;
      });
    } catch (error) {
      if (isUniqueViolation(error)) throw new ProjectExistsError();
      throw error;
    }
  }

  async updateHeader(ownerId: string, id: string, patch: ProjectHeaderPatch) {
    // COALESCE keeps any column the patch leaves undefined.
    const { rowCount } = await this.pool.query(
      `UPDATE projects SET
         name          = COALESCE($3, name),
         customer      = COALESCE($4, customer),
         pic           = COALESCE($5, pic),
         harga_kontrak = COALESCE($6, harga_kontrak),
         updated_at    = now()
       WHERE owner_id = $1 AND id = $2`,
      [ownerId, id, patch.name ?? null, patch.customer ?? null, patch.pic ?? null, patch.hargaKontrak ?? null],
    );
    if (!rowCount) return null;
    return this.findOwned(this.pool, ownerId, id);
  }

  async replaceItems(ownerId: string, id: string, items: CostItem[]) {
    return withTransaction(this.pool, async (client) => {
      // Lock the project row so two concurrent autosaves cannot interleave their
      // DELETE and INSERT and leave a merged item list behind.
      const locked = await client.query("SELECT 1 FROM projects WHERE owner_id = $1 AND id = $2 FOR UPDATE", [
        ownerId,
        id,
      ]);
      if (!locked.rowCount) return null;

      await client.query("DELETE FROM cost_items WHERE project_id = $1", [id]);
      await insertItems(client, id, items);
      await client.query("UPDATE projects SET updated_at = now() WHERE id = $1", [id]);
      return this.findOwned(client, ownerId, id);
    });
  }

  async delete(ownerId: string, id: string) {
    // cost_items rows go with it via ON DELETE CASCADE.
    const { rowCount } = await this.pool.query("DELETE FROM projects WHERE owner_id = $1 AND id = $2", [ownerId, id]);
    return (rowCount ?? 0) > 0;
  }

  private async findOwned(db: Queryable, ownerId: string, id: string): Promise<Project | null> {
    const { rows } = await db.query<ProjectRow>("SELECT * FROM projects WHERE owner_id = $1 AND id = $2", [
      ownerId,
      id,
    ]);
    const row = rows[0];
    if (!row) return null;
    const items = await loadItems(db, [row.id]);
    return toProject(row, items.get(row.id) ?? []);
  }
}
