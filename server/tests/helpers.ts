import type { RequestHandler } from "express";
import request from "supertest";
import { createApp } from "../src/app.js";
import {
  InMemoryProjectRepository,
  InMemoryRefreshTokenRepository,
  InMemoryUserRepository,
} from "../src/repositories/memory.js";

export const TEST_SECRET = "test-access-secret-that-is-at-least-32-characters-long";

const noLimit: RequestHandler = (_req, _res, next) => next();

export interface TestAppOptions {
  now?: () => Date;
  accessTokenTtlSeconds?: number;
  credentialLimiter?: RequestHandler;
  adminEmails?: string[];
}

/**
 * The real Express app wired to in-memory repositories. Exercises routing,
 * validation, auth middleware and the error envelope with no database.
 */
export function buildTestApp(options: TestAppOptions = {}) {
  const repos = {
    users: new InMemoryUserRepository(),
    refreshTokens: new InMemoryRefreshTokenRepository(),
    projects: new InMemoryProjectRepository(),
  };
  const app = createApp({
    config: {
      jwtAccessSecret: TEST_SECRET,
      accessTokenTtlSeconds: options.accessTokenTtlSeconds ?? 900,
      refreshTokenTtlSeconds: 30 * 24 * 60 * 60,
      // Minimum bcrypt cost keeps the suite fast; production uses 12.
      passwordHashRounds: 4,
      corsOrigins: [],
      adminEmails: options.adminEmails ?? [],
      exportTimeZone: "Asia/Jakarta",
    },
    ...repos,
    logger: { error: () => {} },
    credentialLimiter: options.credentialLimiter ?? noLimit,
    ...(options.now ? { now: options.now } : {}),
  });
  return { app, repos, http: request(app) };
}

let counter = 0;

/** Registers a fresh user and returns their tokens plus a ready-made auth header. */
export async function registerUser(http: ReturnType<typeof request>, overrides: Partial<{ email: string }> = {}) {
  counter += 1;
  const res = await http
    .post("/api/v1/auth/register")
    .send({ fullName: `User ${counter}`, email: overrides.email ?? `user${counter}@example.com`, password: "password123" })
    .expect(201);
  const { accessToken, refreshToken, user } = res.body.data;
  return { accessToken, refreshToken, user, auth: { Authorization: `Bearer ${accessToken}` } };
}

/** A project body with one item of every kind. */
export function sampleProject(id = `p${Date.now()}${Math.floor(Math.random() * 1e6)}`) {
  return {
    id,
    name: "Project IT System",
    customer: "PT Maju Jaya",
    pic: "Budi",
    hargaKontrak: 50_000_000,
    items: [
      { id: "i1", kind: "JASA", description: "Rancang bangun", engineer: "Andi", amount: 5_000_000 },
      { id: "i2", kind: "BARANG", description: "Kabel 2m", quantity: 10, amount: 25_000 },
      { id: "i3", kind: "TRANSPORTASI", description: "Sewa mobil", amount: 300_000 },
      { id: "i4", kind: "LAIN_LAIN", description: "Konsumsi", amount: 150_000 },
    ],
  };
}
