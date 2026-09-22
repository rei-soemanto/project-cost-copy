import { describe, expect, it } from "vitest";
import { defaultCredentialLimiter } from "../src/app.js";
import { buildTestApp, registerUser } from "./helpers.js";

describe("POST /auth/register", () => {
  it("creates a user and returns a token pair without the password hash", async () => {
    const { http } = buildTestApp();
    const res = await http
      .post("/api/v1/auth/register")
      .send({ fullName: "Rei Soemanto", email: "Rei@Example.com", password: "password123" })
      .expect(201);

    expect(res.body.data.accessToken).toEqual(expect.any(String));
    expect(res.body.data.refreshToken).toEqual(expect.any(String));
    // Email is normalised to lower case.
    expect(res.body.data.user).toEqual({ id: expect.any(String), email: "rei@example.com", fullName: "Rei Soemanto" });
    expect(JSON.stringify(res.body)).not.toContain("passwordHash");
  });

  it("rejects a duplicate email case-insensitively with 409 EMAIL_TAKEN", async () => {
    const { http } = buildTestApp();
    await registerUser(http, { email: "dupe@example.com" });
    const res = await http
      .post("/api/v1/auth/register")
      .send({ fullName: "Other", email: "DUPE@example.com", password: "password123" })
      .expect(409);
    expect(res.body.error.code).toBe("EMAIL_TAKEN");
  });

  it("rejects a short password and a malformed email with 400 and per-field details", async () => {
    const { http } = buildTestApp();
    const res = await http
      .post("/api/v1/auth/register")
      .send({ fullName: "X", email: "not-an-email", password: "short" })
      .expect(400);
    expect(res.body.error.code).toBe("VALIDATION_ERROR");
    const paths = res.body.error.details.map((d: { path: string }) => d.path);
    expect(paths).toEqual(expect.arrayContaining(["email", "password"]));
  });
});

describe("POST /auth/login", () => {
  it("returns tokens for correct credentials", async () => {
    const { http } = buildTestApp();
    await registerUser(http, { email: "login@example.com" });
    const res = await http
      .post("/api/v1/auth/login")
      .send({ email: "login@example.com", password: "password123" })
      .expect(200);
    expect(res.body.data.accessToken).toEqual(expect.any(String));
  });

  it("gives the same 401 for a wrong password and an unknown email", async () => {
    const { http } = buildTestApp();
    await registerUser(http, { email: "known@example.com" });

    const wrongPassword = await http
      .post("/api/v1/auth/login")
      .send({ email: "known@example.com", password: "wrong-password" })
      .expect(401);
    const unknownEmail = await http
      .post("/api/v1/auth/login")
      .send({ email: "nobody@example.com", password: "password123" })
      .expect(401);

    // Identical responses, so login cannot be used to discover registered emails.
    expect(wrongPassword.body).toEqual(unknownEmail.body);
    expect(wrongPassword.body.error.code).toBe("INVALID_CREDENTIALS");
  });
});

describe("POST /auth/refresh", () => {
  it("rotates: returns a new pair and the old refresh token stops working", async () => {
    const { http } = buildTestApp();
    const { refreshToken } = await registerUser(http);

    const first = await http.post("/api/v1/auth/refresh").send({ refreshToken }).expect(200);
    expect(first.body.data.refreshToken).not.toBe(refreshToken);

    const replay = await http.post("/api/v1/auth/refresh").send({ refreshToken }).expect(401);
    expect(replay.body.error.code).toBe("INVALID_REFRESH_TOKEN");
  });

  it("revokes every session when a spent refresh token is replayed", async () => {
    const { http } = buildTestApp();
    const { refreshToken: stolen } = await registerUser(http);

    // The legitimate client refreshes, getting a fresh token...
    const legit = await http.post("/api/v1/auth/refresh").send({ refreshToken: stolen }).expect(200);
    const legitToken = legit.body.data.refreshToken;

    // ...then an attacker replays the stolen, already-spent token.
    await http.post("/api/v1/auth/refresh").send({ refreshToken: stolen }).expect(401);

    // Reuse was detected, so the legitimate session's token is revoked too.
    const after = await http.post("/api/v1/auth/refresh").send({ refreshToken: legitToken }).expect(401);
    expect(after.body.error.code).toBe("INVALID_REFRESH_TOKEN");
  });

  it("rejects an unknown refresh token", async () => {
    const { http } = buildTestApp();
    const res = await http.post("/api/v1/auth/refresh").send({ refreshToken: "made-up" }).expect(401);
    expect(res.body.error.code).toBe("INVALID_REFRESH_TOKEN");
  });
});

describe("access tokens", () => {
  it("rejects a request with no bearer token", async () => {
    const { http } = buildTestApp();
    const res = await http.get("/api/v1/projects").expect(401);
    expect(res.body.error.code).toBe("UNAUTHORIZED");
  });

  it("rejects a forged token", async () => {
    const { http } = buildTestApp();
    const res = await http.get("/api/v1/projects").set("Authorization", "Bearer not.a.jwt").expect(401);
    expect(res.body.error.code).toBe("UNAUTHORIZED");
  });

  it("answers TOKEN_EXPIRED - not UNAUTHORIZED - for an expired token, so the client knows to refresh", async () => {
    // A 1-second TTL, then wait it out.
    const { http } = buildTestApp({ accessTokenTtlSeconds: 1 });
    const { auth } = await registerUser(http);
    await new Promise((resolve) => setTimeout(resolve, 2100));
    const res = await http.get("/api/v1/projects").set(auth).expect(401);
    expect(res.body.error.code).toBe("TOKEN_EXPIRED");
  });
});

describe("credential rate limiting", () => {
  it("answers 429 RATE_LIMITED past the limit, in the API's error envelope", async () => {
    const { http } = buildTestApp({ credentialLimiter: defaultCredentialLimiter() });
    const attempt = () =>
      http.post("/api/v1/auth/login").send({ email: "someone@example.com", password: "wrong" });

    for (let i = 0; i < 20; i++) await attempt().expect(401);
    const res = await attempt().expect(429);
    expect(res.body.error.code).toBe("RATE_LIMITED");
  });

  it("behind a trusted proxy, limits each client separately by X-Forwarded-For", async () => {
    const { app } = buildTestApp({ credentialLimiter: defaultCredentialLimiter() });
    app.set("trust proxy", 1);
    const http = (await import("supertest")).default(app);
    const attempt = (ip: string) =>
      http.post("/api/v1/auth/login").set("X-Forwarded-For", ip).send({ email: "x@example.com", password: "wrong" });

    for (let i = 0; i < 20; i++) await attempt("203.0.113.1").expect(401);
    await attempt("203.0.113.1").expect(429);
    // A different user behind the same nginx is not locked out.
    await attempt("203.0.113.2").expect(401);
  });

  it("does not limit /refresh", async () => {
    const { http } = buildTestApp({ credentialLimiter: defaultCredentialLimiter() });
    for (let i = 0; i < 25; i++) {
      await http.post("/api/v1/auth/refresh").send({ refreshToken: "x" }).expect(401);
    }
  });
});
