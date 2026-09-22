import type { z } from "zod";
import { AppError } from "../errors/AppError.js";

/**
 * Validates `input` against `schema`, returning the parsed (and transformed)
 * value or throwing a 400 VALIDATION_ERROR listing every problem.
 *
 * Controllers call this directly rather than going through a middleware, so the
 * parsed value keeps its exact static type instead of arriving as `any`.
 */
export function parseOrThrow<S extends z.ZodType>(schema: S, input: unknown): z.output<S> {
  const result = schema.safeParse(input);
  if (!result.success) {
    throw AppError.validation(
      result.error.issues.map((issue) => ({ path: issue.path.join("."), message: issue.message })),
    );
  }
  return result.data;
}
