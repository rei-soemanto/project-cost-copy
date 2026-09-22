import { describe, expect, it } from "vitest";
import { loadConfig } from "../src/config/env.js";

const valid = {
  DATABASE_URL: "postgres://u:p@localhost:5432/db",
  JWT_ACCESS_SECRET: "x".repeat(32),
};

describe("loadConfig", () => {
  it("applies defaults", () => {
    const config = loadConfig(valid);
    expect(config.port).toBe(3000);
    expect(config.nodeEnv).toBe("development");
    expect(config.corsOrigins).toEqual([]);
    expect(config.passwordHashRounds).toBe(12);
  });

  it("parses a comma-separated CORS list, ignoring blanks", () => {
    const config = loadConfig({ ...valid, CORS_ORIGINS: "http://a.com, http://b.com,," });
    expect(config.corsOrigins).toEqual(["http://a.com", "http://b.com"]);
  });

  it("refuses a short JWT secret", () => {
    expect(() => loadConfig({ ...valid, JWT_ACCESS_SECRET: "too-short" })).toThrow(/JWT_ACCESS_SECRET/);
  });

  it("refuses to start without a database URL, naming the missing variable", () => {
    expect(() => loadConfig({ JWT_ACCESS_SECRET: "x".repeat(32) })).toThrow(/DATABASE_URL/);
  });
});
