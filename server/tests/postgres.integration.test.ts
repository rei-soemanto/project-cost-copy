/**
 * Runs the real Postgres repositories through the full HTTP stack.
 *
 * Skipped unless TEST_DATABASE_URL is set, e.g.
 *   TEST_DATABASE_URL=postgres://postgres:PASSWORD@localhost:5432/costproject npm test
 *
 * Each run creates a throwaway schema, migrates into it, and drops it afterwards,
 * so it never reads or writes real data in that database.
 */
import { fileURLToPath } from "node:url";
import type { Pool } from "pg";
import request from "supertest";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { runMigrations } from "../src/db/migrations.js";
import { createPool } from "../src/db/pool.js";
import { PgProjectRepository, PgRefreshTokenRepository, PgUserRepository } from "../src/repositories/postgres.js";
import { TEST_SECRET, sampleProject } from "./helpers.js";

const databaseUrl = process.env.TEST_DATABASE_URL;

describe.skipIf(!databaseUrl)("Postgres repositories (integration)", () => {
  const schema = `costproject_test_${Date.now()}_${Math.floor(Math.random() * 1e6)}`;
  let admin: Pool;
  let pool: Pool;
  let http: ReturnType<typeof request>;

  beforeAll(async () => {
    admin = createPool(databaseUrl!);
    await admin.query(`CREATE SCHEMA "${schema}"`);
    pool = createPool(databaseUrl!, { searchPath: schema });
    await runMigrations(pool, fileURLToPath(new URL("../migrations", import.meta.url)));

    const app = createApp({
      config: {
        jwtAccessSecret: TEST_SECRET,
        accessTokenTtlSeconds: 900,
        refreshTokenTtlSeconds: 3600,
        passwordHashRounds: 4,
        corsOrigins: [],
        adminEmails: ["pg-admin@example.com"],
      },
      users: new PgUserRepository(pool),
      refreshTokens: new PgRefreshTokenRepository(pool),
      projects: new PgProjectRepository(pool),
      logger: { error: () => {} },
      credentialLimiter: (_req, _res, next) => next(),
    });
    http = request(app);
  });

  afterAll(async () => {
    await pool?.end();
    await admin?.query(`DROP SCHEMA IF EXISTS "${schema}" CASCADE`);
    await admin?.end();
  });

  let counter = 0;
  async function register() {
    counter += 1;
    const res = await http
      .post("/api/v1/auth/register")
      .send({ fullName: "Test", email: `pg${counter}@example.com`, password: "password123" })
      .expect(201);
    return { ...res.body.data, auth: { Authorization: `Bearer ${res.body.data.accessToken}` } };
  }

  it("migrations are idempotent", async () => {
    const again = await runMigrations(pool, fileURLToPath(new URL("../migrations", import.meta.url)));
    expect(again).toEqual([]);
  });

  it("enforces case-insensitive email uniqueness through the unique index", async () => {
    await http
      .post("/api/v1/auth/register")
      .send({ fullName: "A", email: "case@example.com", password: "password123" })
      .expect(201);
    const res = await http
      .post("/api/v1/auth/register")
      .send({ fullName: "B", email: "CASE@example.com", password: "password123" })
      .expect(409);
    expect(res.body.error.code).toBe("EMAIL_TAKEN");
  });

  it("round-trips a project with every item kind, preserving order", async () => {
    const { auth } = await register();
    await http.post("/api/v1/projects").set(auth).send(sampleProject("pg-proj")).expect(201);
    const res = await http.get("/api/v1/projects/pg-proj").set(auth).expect(200);

    expect(res.body.data.items.map((i: { kind: string }) => i.kind)).toEqual([
      "JASA",
      "BARANG",
      "TRANSPORTASI",
      "LAIN_LAIN",
    ]);
    expect(res.body.data.items[1]).toMatchObject({ quantity: 10, amount: 25_000 });
  });

  it("returns BIGINT money as JSON numbers, exactly, beyond 32-bit range", async () => {
    // node-postgres returns int8 as a string; the repository must convert it.
    const { auth } = await register();
    const big = 9_000_000_000_000; // 9 trillion rupiah, well past 2^31
    await http.post("/api/v1/projects").set(auth).send({ id: "big", name: "big", hargaKontrak: big }).expect(201);
    const res = await http.get("/api/v1/projects/big").set(auth).expect(200);
    expect(res.body.data.hargaKontrak).toBe(big);
    expect(typeof res.body.data.hargaKontrak).toBe("number");
  });

  it("PATCH leaves unnamed columns untouched (COALESCE)", async () => {
    const { auth } = await register();
    await http.post("/api/v1/projects").set(auth).send(sampleProject("patch-me")).expect(201);
    const res = await http.patch("/api/v1/projects/patch-me").set(auth).send({ pic: "New PIC" }).expect(200);
    expect(res.body.data).toMatchObject({ pic: "New PIC", name: "Project IT System", hargaKontrak: 50_000_000 });
  });

  it("replaceItems swaps the list atomically", async () => {
    const { auth } = await register();
    await http.post("/api/v1/projects").set(auth).send(sampleProject("swap")).expect(201);
    const res = await http
      .put("/api/v1/projects/swap/items")
      .set(auth)
      .send({ items: [{ id: "only", kind: "JASA", description: "x", amount: 7 }] })
      .expect(200);
    expect(res.body.data.items).toEqual([
      { id: "only", kind: "JASA", description: "x", engineer: "", quantity: null, amount: 7 },
    ]);
  });

  it("isolates projects between users", async () => {
    const alice = await register();
    const bob = await register();
    await http.post("/api/v1/projects").set(alice.auth).send(sampleProject("pg-alice")).expect(201);
    await http.get("/api/v1/projects/pg-alice").set(bob.auth).expect(404);
    await http.delete("/api/v1/projects/pg-alice").set(bob.auth).expect(404);
    await http.get("/api/v1/projects/pg-alice").set(alice.auth).expect(200);
  });

  it("deleting a project cascades to its items", async () => {
    const { auth } = await register();
    await http.post("/api/v1/projects").set(auth).send(sampleProject("cascade")).expect(201);
    await http.delete("/api/v1/projects/cascade").set(auth).expect(204);
    const { rows } = await pool.query("SELECT count(*)::int AS n FROM cost_items WHERE project_id = 'cascade'");
    expect(rows[0].n).toBe(0);
  });

  it("lets exactly one of two concurrent refreshes with the same token succeed", async () => {
    const { refreshToken } = await register();
    const [a, b] = await Promise.all([
      http.post("/api/v1/auth/refresh").send({ refreshToken }),
      http.post("/api/v1/auth/refresh").send({ refreshToken }),
    ]);
    expect([a.status, b.status].sort()).toEqual([200, 401]);
  });

  it("admin export reads every user's projects and owners from Postgres", async () => {
    const adminRes = await http
      .post("/api/v1/auth/register")
      .send({ fullName: "PG Admin", email: "pg-admin@example.com", password: "password123" })
      .expect(201);
    const adminAuth = { Authorization: `Bearer ${adminRes.body.data.accessToken}` };
    const other = await register();
    await http.post("/api/v1/projects").set(other.auth).send({ ...sampleProject("pg-export"), name: "PG Export" }).expect(201);

    const res = await http
      .get("/api/v1/export/all.xlsx")
      .set(adminAuth)
      .buffer(true)
      .parse((r, done) => {
        const chunks: Buffer[] = [];
        r.on("data", (c: Buffer) => chunks.push(c));
        r.on("end", () => done(null, Buffer.concat(chunks)));
      })
      .expect(200);
    const ExcelJS = (await import("exceljs")).default;
    const wb = new ExcelJS.Workbook();
    await wb.xlsx.load(res.body as unknown as ArrayBuffer);
    const names: unknown[] = [];
    wb.getWorksheet("Ringkasan Project")!.eachRow((row, n) => n > 1 && names.push(row.getCell(3).value));
    expect(names).toContain("PG Export");
  });

  it("findManyByIds returns known users and skips unknown ids", async () => {
    const { user } = await register();
    const users = new PgUserRepository(pool);
    const found = await users.findManyByIds([user.id, "00000000-0000-0000-0000-000000000000"]);
    expect(found.map((u) => u.id)).toEqual([user.id]);
    expect(await users.findManyByIds([])).toEqual([]);
  });

  it("stores refresh tokens only as hashes", async () => {
    const { refreshToken } = await register();
    const { rows } = await pool.query("SELECT count(*)::int AS n FROM refresh_tokens WHERE token_hash = $1", [
      refreshToken,
    ]);
    expect(rows[0].n).toBe(0);
  });
});
