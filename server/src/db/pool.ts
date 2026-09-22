import pg from "pg";
import type { Pool, PoolClient } from "pg";

export interface PoolOptions {
  /**
   * Schema to resolve unqualified table names in. The integration tests use a
   * throwaway schema per run so they never touch real data.
   */
  searchPath?: string;
}

export function createPool(databaseUrl: string, options: PoolOptions = {}): Pool {
  return new pg.Pool({
    connectionString: databaseUrl,
    max: 10,
    ...(options.searchPath ? { options: `-c search_path=${options.searchPath}` } : {}),
  });
}

/**
 * Runs `fn` inside a transaction, committing on success and rolling back on any
 * throw. The client is always released, even if rollback itself fails.
 */
export async function withTransaction<T>(pool: Pool, fn: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const result = await fn(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {
      // The original error is the one worth surfacing.
    });
    throw error;
  } finally {
    client.release();
  }
}

/** Postgres SQLSTATE for a unique constraint violation. */
export const UNIQUE_VIOLATION = "23505";

export function isUniqueViolation(error: unknown): boolean {
  return typeof error === "object" && error !== null && "code" in error && error.code === UNIQUE_VIOLATION;
}

/**
 * node-postgres returns BIGINT columns as strings, because a JS number cannot
 * hold every int8 exactly. Rupiah amounts sit far below 2^53, but this refuses
 * to silently round anything that does not fit rather than trusting that.
 */
export function toSafeInt(value: string | number, column: string): number {
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isSafeInteger(n)) {
    throw new Error(`Column ${column} holds ${value}, which is outside the safe integer range`);
  }
  return n;
}
