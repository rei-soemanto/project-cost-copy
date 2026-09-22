import { randomBytes } from "node:crypto";
import type { CostItem, Project } from "../domain/types.js";
import { AppError } from "../errors/AppError.js";
import { ProjectExistsError, type ProjectHeaderPatch, type ProjectRepository } from "../repositories/types.js";

export interface CreateProjectInput {
  id?: string | undefined;
  name: string;
  customer: string;
  pic: string;
  hargaKontrak: number;
  items: CostItem[];
}

/** Same 24-hex-char shape the Kotlin client generates. */
const newProjectId = () => randomBytes(12).toString("hex");

/**
 * Project use cases. Ownership is enforced by the repository (every call is
 * scoped to `userId`); this layer turns "not found" into the right HTTP error.
 */
export class ProjectService {
  constructor(private readonly projects: ProjectRepository) {}

  list(userId: string): Promise<Project[]> {
    return this.projects.listByOwner(userId);
  }

  async get(userId: string, id: string): Promise<Project> {
    return orNotFound(await this.projects.findById(userId, id));
  }

  async create(userId: string, input: CreateProjectInput): Promise<Project> {
    try {
      return await this.projects.create({
        id: input.id ?? newProjectId(),
        ownerId: userId,
        name: input.name,
        customer: input.customer,
        pic: input.pic,
        hargaKontrak: input.hargaKontrak,
        items: input.items,
      });
    } catch (error) {
      if (error instanceof ProjectExistsError) {
        throw new AppError(409, "PROJECT_EXISTS", "A project with this id already exists");
      }
      throw error;
    }
  }

  async updateHeader(userId: string, id: string, patch: ProjectHeaderPatch): Promise<Project> {
    return orNotFound(await this.projects.updateHeader(userId, id, patch));
  }

  async replaceItems(userId: string, id: string, items: CostItem[]): Promise<Project> {
    return orNotFound(await this.projects.replaceItems(userId, id, items));
  }

  async delete(userId: string, id: string): Promise<void> {
    if (!(await this.projects.delete(userId, id))) throw AppError.notFound("Project");
  }
}

/**
 * A project owned by someone else comes back as null from the repository, so it
 * answers 404 exactly like a missing one - ids cannot be probed for existence.
 */
function orNotFound(project: Project | null): Project {
  if (!project) throw AppError.notFound("Project");
  return project;
}
