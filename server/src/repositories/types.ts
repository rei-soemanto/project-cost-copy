import type { CostItem, Project, User } from "../domain/types.js";

/**
 * Repository interfaces. Services depend only on these, never on `pg` directly,
 * so tests can inject the in-memory implementations and exercise the full HTTP
 * stack without a database.
 *
 * Every project method takes `ownerId` and returns null / false for projects
 * owned by someone else - ownership is enforced here, at the lowest layer, so a
 * forgotten check in a service cannot leak another user's data.
 */

export interface NewUser {
  email: string;
  fullName: string;
  passwordHash: string;
}

export interface UserRepository {
  /** Case-insensitive. */
  findByEmail(email: string): Promise<User | null>;
  findById(id: string): Promise<User | null>;
  /** Users with these ids; unknown ids are skipped. */
  findManyByIds(ids: string[]): Promise<User[]>;
  /** Throws EmailTakenError if the email is already registered. */
  create(input: NewUser): Promise<User>;
}

export type ConsumeResult =
  | { status: "ok"; userId: string }
  /** The token was already used. Signals possible theft; the caller revokes the user's sessions. */
  | { status: "reused"; userId: string }
  | { status: "invalid" };

export interface RefreshTokenRepository {
  create(input: { userId: string; tokenHash: string; expiresAt: Date }): Promise<void>;
  /**
   * Atomically marks a valid token as used. Exactly one of two concurrent calls
   * with the same token can succeed.
   */
  consume(tokenHash: string, now: Date): Promise<ConsumeResult>;
  revokeAllForUser(userId: string, now: Date): Promise<void>;
}

export interface NewProject {
  id: string;
  ownerId: string;
  name: string;
  customer: string;
  pic: string;
  hargaKontrak: number;
  items: CostItem[];
}

export interface ProjectHeaderPatch {
  name?: string | undefined;
  customer?: string | undefined;
  pic?: string | undefined;
  hargaKontrak?: number | undefined;
}

export interface ProjectRepository {
  /** Newest first, items included. */
  listByOwner(ownerId: string): Promise<Project[]>;
  /**
   * Every user's projects, newest first, items included. The one method not
   * scoped to an owner: only the admin export may call it.
   */
  listAll(): Promise<Project[]>;
  findById(ownerId: string, id: string): Promise<Project | null>;
  /** Throws ProjectExistsError if the id is already used by anyone. */
  create(input: NewProject): Promise<Project>;
  updateHeader(ownerId: string, id: string, patch: ProjectHeaderPatch): Promise<Project | null>;
  /** Replaces the whole item list in one transaction. Null if not found / not owned. */
  replaceItems(ownerId: string, id: string, items: CostItem[]): Promise<Project | null>;
  /** True if a project was deleted. */
  delete(ownerId: string, id: string): Promise<boolean>;
}

/** Thrown by UserRepository.create on a duplicate email. */
export class EmailTakenError extends Error {
  constructor() {
    super("Email already registered");
    this.name = "EmailTakenError";
  }
}

/** Thrown by ProjectRepository.create on a duplicate id. */
export class ProjectExistsError extends Error {
  constructor() {
    super("Project id already exists");
    this.name = "ProjectExistsError";
  }
}
