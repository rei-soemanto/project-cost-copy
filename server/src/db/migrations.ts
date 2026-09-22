import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import type { Pool } from "pg";
import { withTransaction } from "./pool.js";

/**
 * Applies every `migrations/NNN_*.sql` file not yet recorded in
 * schema_migrations, in filename order. Each file runs in its own transaction,
 * so a failing migration leaves the database exactly as it was before it.
 *
 * Returns the names of the migrations applied by this call.
 */
export async function runMigrations(pool: Pool, migrationsDir: string): Promise<string[]> {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      name       text        PRIMARY KEY,
      applied_at timestamptz NOT NULL DEFAULT now()
    )
  `);

  const files = (await readdir(migrationsDir)).filter((f) => /^\d+_.*\.sql$/.test(f)).sort();
  const { rows } = await pool.query<{ name: string }>("SELECT name FROM schema_migrations");
  const applied = new Set(rows.map((r) => r.name));

  const newlyApplied: string[] = [];
  for (const file of files) {
    if (applied.has(file)) continue;
    const sql = await readFile(path.join(migrationsDir, file), "utf8");
    await withTransaction(pool, async (client) => {
      await client.query(sql);
      await client.query("INSERT INTO schema_migrations (name) VALUES ($1)", [file]);
    });
    newlyApplied.push(file);
  }
  return newlyApplied;
}
