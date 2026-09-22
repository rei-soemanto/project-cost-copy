import { Router, type RequestHandler } from "express";
import type { ProjectsController } from "../controllers/projects.controller.js";

export function projectRoutes(controller: ProjectsController, requireAuth: RequestHandler): Router {
  const router = Router();
  router.use(requireAuth);
  router.get("/", controller.list);
  router.post("/", controller.create);
  router.get("/:id", controller.get);
  router.patch("/:id", controller.update);
  router.put("/:id/items", controller.replaceItems);
  router.delete("/:id", controller.remove);
  return router;
}
