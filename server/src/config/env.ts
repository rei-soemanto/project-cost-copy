import { z } from "zod";

/**
 * Environment is parsed once at startup and handed to createApp() as a plain
 * object. Nothing else reads process.env, so tests build a config directly
 * instead of mutating globals.
 */
const EnvSchema = z.object({
  PORT: z.coerce.number().int().positive().default(3000),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  DATABASE_URL: z.string().min(1, "DATABASE_URL is required"),
  JWT_ACCESS_SECRET: z.string().min(32, "JWT_ACCESS_SECRET must be at least 32 characters"),
  CORS_ORIGINS: z.string().default(""),
});

export interface AppConfig {
  port: number;
  nodeEnv: "development" | "test" | "production";
  databaseUrl: string;
  jwtAccessSecret: string;
  corsOrigins: string[];
  accessTokenTtlSeconds: number;
  refreshTokenTtlSeconds: number;
  /** bcrypt cost factor. 12 in production; tests lower it to stay fast. */
  passwordHashRounds: number;
}

export const ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
export const REFRESH_TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60;

export function loadConfig(env: NodeJS.ProcessEnv): AppConfig {
  const parsed = EnvSchema.safeParse(env);
  if (!parsed.success) {
    const problems = parsed.error.issues.map((i) => `  - ${i.path.join(".")}: ${i.message}`).join("\n");
    throw new Error(`Invalid environment configuration:\n${problems}`);
  }
  const e = parsed.data;

  return {
    port: e.PORT,
    nodeEnv: e.NODE_ENV,
    databaseUrl: e.DATABASE_URL,
    jwtAccessSecret: e.JWT_ACCESS_SECRET,
    corsOrigins: e.CORS_ORIGINS.split(",").map((s) => s.trim()).filter(Boolean),
    accessTokenTtlSeconds: ACCESS_TOKEN_TTL_SECONDS,
    refreshTokenTtlSeconds: REFRESH_TOKEN_TTL_SECONDS,
    passwordHashRounds: 12,
  };
}
