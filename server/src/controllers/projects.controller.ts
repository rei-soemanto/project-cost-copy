import type { Request, Response } from "express";
import type { Project } from "../domain/types.js";
import { currentUserId } from "../middleware/auth.js";
import { parseOrThrow } from "../schemas/parse.js";
import {
  CreateProjectSchema,
  ProjectParamsSchema,
  ReplaceItemsSchema,
  UpdateProjectSchema,
} from "../schemas/project.schema.js";
import type { ProjectService } from "../services/projects.service.js";

/** API representation of a project. Omits ownerId; dates become ISO strings. */
function toResponse(project: Project) {
  return {
    id: project.id,
    name: project.name,
    customer: project.customer,
    pic: project.pic,
    hargaKontrak: project.hargaKontrak,
    items: project.items,
    createdAt: project.createdAt.toISOString(),
    updatedAt: project.updatedAt.toISOString(),
  };
}

export class ProjectsController {
  constructor(private readonly projects: ProjectService) {}

  list = async (req: Request, res: Response) => {
    const projects = await this.projects.list(currentUserId(req));
    res.json({ data: projects.map(toResponse) });
  };

  get = async (req: Request, res: Response) => {
    const { id } = parseOrThrow(ProjectParamsSchema, req.params);
    res.json({ data: toResponse(await this.projects.get(currentUserId(req), id)) });
  };

  create = async (req: Request, res: Response) => {
    const body = parseOrThrow(CreateProjectSchema, req.body);
    const project = await this.projects.create(currentUserId(req), body);
    res.status(201).json({ data: toResponse(project) });
  };

  update = async (req: Request, res: Response) => {
    const { id } = parseOrThrow(ProjectParamsSchema, req.params);
    const patch = parseOrThrow(UpdateProjectSchema, req.body);
    res.json({ data: toResponse(await this.projects.updateHeader(currentUserId(req), id, patch)) });
  };

  replaceItems = async (req: Request, res: Response) => {
    const { id } = parseOrThrow(ProjectParamsSchema, req.params);
    const { items } = parseOrThrow(ReplaceItemsSchema, req.body);
    res.json({ data: toResponse(await this.projects.replaceItems(currentUserId(req), id, items)) });
  };

  remove = async (req: Request, res: Response) => {
    const { id } = parseOrThrow(ProjectParamsSchema, req.params);
    await this.projects.delete(currentUserId(req), id);
    res.status(204).end();
  };
}
