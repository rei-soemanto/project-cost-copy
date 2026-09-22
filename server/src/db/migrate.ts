/**
 * CLI: `npm run migrate`. Needs only DATABASE_URL, so it runs before the rest of
 * the environment (JWT secret etc.) has been configured.
 */
import "dotenv/config";
import { fileURLToPath } from "node:url";
import { createPool } from "./pool.js";
import { runMigrations } from "./migrations.js";

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) {
  console.error("DATABASE_URL is not set. Copy .env.example to .env and fill it in.");
  process.exit(1);
}

// Resolves to server/migrations from both src/db (tsx) and dist/db (compiled).
const migrationsDir = fileURLToPath(new URL("../../migrations", import.meta.url));

const pool = createPool(databaseUrl);
try {
  const applied = await runMigrations(pool, migrationsDir);
  console.log(applied.length ? `Applied: ${applied.join(", ")}` : "Database is up to date.");
} catch (error) {
  console.error("Migration failed:", error);
  process.exitCode = 1;
} finally {
  await pool.end();
}
