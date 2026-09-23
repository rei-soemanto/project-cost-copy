import { randomUUID } from "node:crypto";
import type { CostItem, Project, User } from "../domain/types.js";
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

/**
 * In-memory repositories. Used by the test suite so the whole HTTP contract can
 * be exercised without Postgres. They implement the same semantics as the pg
 * versions, including ownership scoping and refresh-token reuse detection.
 *
 * Values are cloned on the way in and out so a caller mutating a returned object
 * cannot corrupt stored state - the same isolation a real database gives.
 */

const clone = <T>(value: T): T => structuredClone(value);

export class InMemoryUserRepository implements UserRepository {
  private readonly users = new Map<string, User>();

  async findByEmail(email: string) {
    const needle = email.toLowerCase();
    for (const user of this.users.values()) {
      if (user.email.toLowerCase() === needle) return clone(user);
    }
    return null;
  }

  async findById(id: string) {
    const user = this.users.get(id);
    return user ? clone(user) : null;
  }

  async findManyByIds(ids: string[]) {
    return ids.flatMap((id) => {
      const user = this.users.get(id);
      return user ? [clone(user)] : [];
    });
  }

  async create(input: NewUser) {
    if (await this.findByEmail(input.email)) throw new EmailTakenError();
    const user: User = { id: randomUUID(), createdAt: new Date(), ...input };
    this.users.set(user.id, user);
    return clone(user);
  }
}

interface StoredToken {
  userId: string;
  expiresAt: Date;
  revokedAt: Date | null;
}

export class InMemoryRefreshTokenRepository implements RefreshTokenRepository {
  private readonly tokens = new Map<string, StoredToken>();

  async create(input: { userId: string; tokenHash: string; expiresAt: Date }) {
    this.tokens.set(input.tokenHash, { userId: input.userId, expiresAt: input.expiresAt, revokedAt: null });
  }

  async consume(tokenHash: string, now: Date): Promise<ConsumeResult> {
    const token = this.tokens.get(tokenHash);
    if (!token) return { status: "invalid" };
    if (token.revokedAt) return { status: "reused", userId: token.userId };
    if (token.expiresAt <= now) return { status: "invalid" };
    token.revokedAt = now;
    return { status: "ok", userId: token.userId };
  }

  async revokeAllForUser(userId: string, now: Date) {
    for (const token of this.tokens.values()) {
      if (token.userId === userId && !token.revokedAt) token.revokedAt = now;
    }
  }
}

export class InMemoryProjectRepository implements ProjectRepository {
  private readonly projects = new Map<string, Project>();

  async listByOwner(ownerId: string) {
    return [...this.projects.values()]
      .filter((p) => p.ownerId === ownerId)
      .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
      .map(clone);
  }

  async listAll() {
    return [...this.projects.values()].sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime()).map(clone);
  }

  async findById(ownerId: string, id: string) {
    const project = this.projects.get(id);
    return project && project.ownerId === ownerId ? clone(project) : null;
  }

  async create(input: NewProject) {
    if (this.projects.has(input.id)) throw new ProjectExistsError();
    const now = new Date();
    const project: Project = { ...clone(input), createdAt: now, updatedAt: now };
    this.projects.set(project.id, project);
    return clone(project);
  }

  async updateHeader(ownerId: string, id: string, patch: ProjectHeaderPatch) {
    const project = this.projects.get(id);
    if (!project || project.ownerId !== ownerId) return null;
    if (patch.name !== undefined) project.name = patch.name;
    if (patch.customer !== undefined) project.customer = patch.customer;
    if (patch.pic !== undefined) project.pic = patch.pic;
    if (patch.hargaKontrak !== undefined) project.hargaKontrak = patch.hargaKontrak;
    project.updatedAt = new Date();
    return clone(project);
  }

  async replaceItems(ownerId: string, id: string, items: CostItem[]) {
    const project = this.projects.get(id);
    if (!project || project.ownerId !== ownerId) return null;
    project.items = clone(items);
    project.updatedAt = new Date();
    return clone(project);
  }

  async delete(ownerId: string, id: string) {
    const project = this.projects.get(id);
    if (!project || project.ownerId !== ownerId) return false;
    this.projects.delete(id);
    return true;
  }
}
