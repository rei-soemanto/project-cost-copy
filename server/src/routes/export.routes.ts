import { Router, type RequestHandler } from "express";
import type { AccountController } from "../controllers/account.controller.js";
import type { ExportController } from "../controllers/export.controller.js";

export function accountRoutes(controller: AccountController, requireAuth: RequestHandler): Router {
  const router = Router();
  router.use(requireAuth);
  router.get("/", controller.me);
  return router;
}

export function exportRoutes(controller: ExportController, requireAuth: RequestHandler): Router {
  const router = Router();
  router.use(requireAuth);
  router.get("/projects.xlsx", controller.mine);
  router.get("/all.xlsx", controller.all);
  return router;
}
