import type { NextFunction, Request, RequestHandler, Response } from "express";
import { AppError } from "../errors/AppError.js";
import type { AuthService } from "../services/auth.service.js";

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      /** Set by requireAuth once the bearer token is verified. */
      userId?: string;
    }
  }
}

/** Rejects the request unless it carries a valid `Authorization: Bearer <token>`. */
export function requireAuth(auth: AuthService): RequestHandler {
  return (req: Request, _res: Response, next: NextFunction) => {
    const header = req.headers.authorization;
    const match = header?.match(/^Bearer\s+(.+)$/i);
    if (!match?.[1]) throw AppError.unauthorized();

    req.userId = auth.verifyAccessToken(match[1]);
    next();
  };
}

/**
 * The authenticated user's id. Throws rather than returning undefined, so a route
 * accidentally mounted without requireAuth fails closed instead of querying with
 * no owner.
 */
export function currentUserId(req: Request): string {
  if (!req.userId) throw AppError.unauthorized();
  return req.userId;
}
