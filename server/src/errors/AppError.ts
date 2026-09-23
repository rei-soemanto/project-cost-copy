/** Every error code the API can return. Mirrors the table in API.md. */
export type ErrorCode =
  | "VALIDATION_ERROR"
  | "UNAUTHORIZED"
  | "TOKEN_EXPIRED"
  | "INVALID_CREDENTIALS"
  | "INVALID_REFRESH_TOKEN"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "EMAIL_TAKEN"
  | "PROJECT_EXISTS"
  | "RATE_LIMITED"
  | "INTERNAL_ERROR";

/**
 * An error that is safe to show the client. Anything thrown that is not an
 * AppError is treated as a bug: logged in full, answered with a generic 500.
 */
export class AppError extends Error {
  constructor(
    readonly status: number,
    readonly code: ErrorCode,
    message: string,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = "AppError";
  }

  static validation(details: unknown) {
    return new AppError(400, "VALIDATION_ERROR", "Request validation failed", details);
  }

  static unauthorized(message = "Authentication required") {
    return new AppError(401, "UNAUTHORIZED", message);
  }

  static tokenExpired() {
    return new AppError(401, "TOKEN_EXPIRED", "Access token expired");
  }

  static forbidden(message = "You do not have permission to do this") {
    return new AppError(403, "FORBIDDEN", message);
  }

  static notFound(what = "Resource") {
    return new AppError(404, "NOT_FOUND", `${what} not found`);
  }
}
