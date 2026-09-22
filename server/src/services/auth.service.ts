import { createHash, randomBytes } from "node:crypto";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import type { AppConfig } from "../config/env.js";
import { toPublicUser, type PublicUser } from "../domain/types.js";
import { AppError } from "../errors/AppError.js";
import { EmailTakenError, type RefreshTokenRepository, type UserRepository } from "../repositories/types.js";

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export interface AuthResult extends TokenPair {
  user: PublicUser;
}

type Clock = () => Date;

/** Refresh tokens are stored only as this hash, never in the clear. */
const hashToken = (token: string) => createHash("sha256").update(token).digest("hex");

export class AuthService {
  /**
   * Compared against when a login names an unknown email, so that path costs the
   * same bcrypt time as a real one and response timing does not reveal which
   * emails are registered.
   */
  private dummyHash: Promise<string> | undefined;

  constructor(
    private readonly users: UserRepository,
    private readonly refreshTokens: RefreshTokenRepository,
    private readonly config: Pick<
      AppConfig,
      "jwtAccessSecret" | "accessTokenTtlSeconds" | "refreshTokenTtlSeconds" | "passwordHashRounds"
    >,
    private readonly now: Clock = () => new Date(),
  ) {}

  async register(input: { fullName: string; email: string; password: string }): Promise<AuthResult> {
    const passwordHash = await bcrypt.hash(input.password, this.config.passwordHashRounds);
    try {
      const user = await this.users.create({ email: input.email, fullName: input.fullName, passwordHash });
      return { ...(await this.issueTokens(user.id)), user: toPublicUser(user) };
    } catch (error) {
      if (error instanceof EmailTakenError) {
        throw new AppError(409, "EMAIL_TAKEN", "An account with this email already exists");
      }
      throw error;
    }
  }

  async login(input: { email: string; password: string }): Promise<AuthResult> {
    const user = await this.users.findByEmail(input.email);
    const hash = user?.passwordHash ?? (await this.getDummyHash());
    const valid = await bcrypt.compare(input.password, hash);

    // One message for both "no such user" and "wrong password".
    if (!user || !valid) {
      throw new AppError(401, "INVALID_CREDENTIALS", "Email or password is incorrect");
    }
    return { ...(await this.issueTokens(user.id)), user: toPublicUser(user) };
  }

  /**
   * Exchanges a refresh token for a new pair and retires the old one (rotation).
   *
   * Presenting an already-used token means it was copied - either the client
   * replayed it or someone stole it. Every session for that user is revoked, so a
   * thief's copy dies along with the legitimate one.
   */
  async refresh(refreshToken: string): Promise<TokenPair> {
    const result = await this.refreshTokens.consume(hashToken(refreshToken), this.now());

    if (result.status === "reused") {
      await this.refreshTokens.revokeAllForUser(result.userId, this.now());
    }
    if (result.status !== "ok") {
      throw new AppError(401, "INVALID_REFRESH_TOKEN", "Refresh token is invalid, expired, or already used");
    }
    return this.issueTokens(result.userId);
  }

  /** Returns the user id in a valid access token. Used by the auth middleware. */
  verifyAccessToken(token: string): string {
    try {
      const payload = jwt.verify(token, this.config.jwtAccessSecret, { algorithms: ["HS256"] });
      if (typeof payload === "string" || typeof payload.sub !== "string") throw AppError.unauthorized();
      return payload.sub;
    } catch (error) {
      if (error instanceof jwt.TokenExpiredError) throw AppError.tokenExpired();
      if (error instanceof AppError) throw error;
      throw AppError.unauthorized("Invalid access token");
    }
  }

  private async issueTokens(userId: string): Promise<TokenPair> {
    const accessToken = jwt.sign({ sub: userId }, this.config.jwtAccessSecret, {
      algorithm: "HS256",
      expiresIn: this.config.accessTokenTtlSeconds,
    });

    const refreshToken = randomBytes(48).toString("base64url");
    await this.refreshTokens.create({
      userId,
      tokenHash: hashToken(refreshToken),
      expiresAt: new Date(this.now().getTime() + this.config.refreshTokenTtlSeconds * 1000),
    });

    return { accessToken, refreshToken };
  }

  private getDummyHash(): Promise<string> {
    this.dummyHash ??= bcrypt.hash("dummy-password-for-timing", this.config.passwordHashRounds);
    return this.dummyHash;
  }
}
