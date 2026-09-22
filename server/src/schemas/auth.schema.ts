import { z } from "zod";

const Email = z.string().trim().toLowerCase().max(254).pipe(z.email());

export const RegisterSchema = z.object({
  fullName: z.string().trim().min(1).max(100),
  email: Email,
  password: z.string().min(8, "Password must be at least 8 characters").max(128),
});

export const LoginSchema = z.object({
  email: Email,
  // Deliberately no minimum length: a wrong password is a 401, not a 400, so the
  // response never hints at the password policy.
  password: z.string().min(1).max(128),
});

export const RefreshSchema = z.object({
  refreshToken: z.string().min(1).max(512),
});
