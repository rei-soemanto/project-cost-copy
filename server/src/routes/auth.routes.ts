import { Router, type RequestHandler } from "express";
import type { AuthController } from "../controllers/auth.controller.js";

export function authRoutes(controller: AuthController, credentialLimiter: RequestHandler): Router {
  const router = Router();
  // Only the credential endpoints are limited, to slow password guessing.
  // /refresh is not: refresh tokens are 384-bit random so guessing is hopeless,
  // and every client refreshes every 15 minutes - limiting it would throttle a
  // whole office sharing one IP.
  router.post("/register", credentialLimiter, controller.register);
  router.post("/login", credentialLimiter, controller.login);
  router.post("/refresh", controller.refresh);
  return router;
}
