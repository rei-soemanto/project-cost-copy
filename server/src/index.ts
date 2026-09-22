import "dotenv/config";
import { createApp } from "./app.js";
import { loadConfig } from "./config/env.js";
import { createPool } from "./db/pool.js";
import { PgProjectRepository, PgRefreshTokenRepository, PgUserRepository } from "./repositories/postgres.js";

const config = loadConfig(process.env);
const pool = createPool(config.databaseUrl);

const app = createApp({
  config,
  users: new PgUserRepository(pool),
  refreshTokens: new PgRefreshTokenRepository(pool),
  projects: new PgProjectRepository(pool),
});

const server = app.listen(config.port, () => {
  console.log(`CostProject API listening on http://localhost:${config.port} (${config.nodeEnv})`);
});

// Stop accepting connections, let in-flight requests finish, then close the pool.
function shutdown(signal: string) {
  console.log(`${signal} received, shutting down`);
  server.close(() => {
    void pool.end().then(() => process.exit(0));
  });
}
process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
