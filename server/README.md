# CostProject API

Express + TypeScript + PostgreSQL backend for the CostProject app.
The HTTP contract is in [API.md](API.md).

## First-time setup

Requires Node 20+ and PostgreSQL (a local PostgreSQL 18 service works).

```sh
cd server
npm install

# 1. Create the database
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "CREATE DATABASE costproject;"

# 2. Configure: copy the template, then set DATABASE_URL and JWT_ACCESS_SECRET
cp .env.example .env

# 3. Create the tables
npm run migrate

# 4. Run with auto-reload
npm run dev
```

Check it is up: `curl http://localhost:3000/health`

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Run from source with reload on change |
| `npm run build` | Compile to `dist/` |
| `npm start` | Run the compiled build |
| `npm run migrate` | Apply pending SQL migrations from `migrations/` |
| `npm run typecheck` | Type-check without emitting |
| `npm test` | Run the test suite |

## Tests

`npm test` needs no database: it drives the real Express app through supertest
with in-memory repositories, covering routing, validation, auth and the error
envelope.

The Postgres repositories have their own integration suite, skipped unless you
point it at a database. It works in a throwaway schema and drops it afterwards,
so it is safe to aim at your development database:

```sh
TEST_DATABASE_URL=postgres://postgres:PASSWORD@localhost:5432/costproject npm test
```

## Layout

Requests flow `routes → controllers → services → repositories`.

```
src/
  index.ts          startup: read env, open pool, listen
  app.ts            createApp(deps) - builds the app from injected dependencies
  config/env.ts     environment parsing and validation
  routes/           URL → controller wiring, auth and rate limiting
  controllers/      validate input, call a service, wrap the response
  services/         business rules (auth flow, project use cases)
  repositories/     data access: types.ts (interfaces), postgres.ts, memory.ts
  schemas/          Zod request schemas - the executable form of API.md
  middleware/       bearer-token auth, central error handler
  db/               connection pool, transactions, migration runner
migrations/         numbered SQL files, applied in order
```

Services depend only on the repository interfaces, which is what lets the tests
swap in `memory.ts` for `postgres.ts`.

## Design notes

- **Money is integer rupiah** end to end: `BIGINT` in Postgres, integer JSON on
  the wire. `pg` returns `BIGINT` as a string; `toSafeInt` in `db/pool.ts`
  converts it and refuses anything it cannot represent exactly.
- **Ownership is enforced in the repository.** Every project query filters on
  the owner, and another user's project answers `404`, not `403`.
- **Refresh tokens rotate** and are stored only as SHA-256 hashes. Replaying a
  spent token revokes all of that user's sessions.
- **Login and register are rate limited** (20 per 15 minutes per IP). If you
  deploy behind a reverse proxy, set Express's `trust proxy` so the limiter sees
  real client IPs instead of the proxy's.
