import type { ErrorRequestHandler, RequestHandler } from "express";
import { AppError } from "../errors/AppError.js";

export interface Logger {
  error(message: string, meta?: unknown): void;
}

/** Unknown route. Mounted after every real route. */
export const notFoundHandler: RequestHandler = (req, _res, next) => {
  next(new AppError(404, "NOT_FOUND", `No route for ${req.method} ${req.path}`));
};

/**
 * Turns every thrown error into the API's error envelope.
 *
 * Express 5 forwards rejected promises from async handlers here on its own, so
 * route handlers can simply throw - no try/catch or async wrapper needed.
 */
export function errorHandler(logger: Logger): ErrorRequestHandler {
  // Express identifies error handlers by their four-parameter signature.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  return (error, req, res, _next) => {
    if (error instanceof AppError) {
      res.status(error.status).json({
        error: {
          code: error.code,
          message: error.message,
          ...(error.details === undefined ? {} : { details: error.details }),
        },
      });
      return;
    }

    // Errors raised by express.json() while reading the body.
    if (isBodyParserError(error)) {
      const tooLarge = error.type === "entity.too.large";
      res.status(tooLarge ? 413 : 400).json({
        error: {
          code: "VALIDATION_ERROR",
          message: tooLarge ? "Request body too large" : "Request body is not valid JSON",
        },
      });
      return;
    }

    // Anything else is a bug. Log it in full; tell the client nothing specific.
    logger.error(`Unhandled error on ${req.method} ${req.path}`, error);
    res.status(500).json({ error: { code: "INTERNAL_ERROR", message: "Something went wrong" } });
  };
}

function isBodyParserError(error: unknown): error is { type: string } {
  return (
    typeof error === "object" &&
    error !== null &&
    "type" in error &&
    typeof error.type === "string" &&
    error.type.startsWith("entity.")
  );
}
