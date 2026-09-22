import cors from "cors";
import express, { type Express, type RequestHandler } from "express";
import { rateLimit } from "express-rate-limit";
import helmet from "helmet";
import type { AppConfig } from "./config/env.js";
import { AuthController } from "./controllers/auth.controller.js";
import { ProjectsController } from "./controllers/projects.controller.js";
import { requireAuth } from "./middleware/auth.js";
import { errorHandler, notFoundHandler, type Logger } from "./middleware/errorHandler.js";
import type { ProjectRepository, RefreshTokenRepository, UserRepository } from "./repositories/types.js";
import { authRoutes } from "./routes/auth.routes.js";
import { projectRoutes } from "./routes/projects.routes.js";
import { AuthService } from "./services/auth.service.js";
import { ProjectService } from "./services/projects.service.js";

export interface AppDeps {
  config: Pick<
    AppConfig,
    "jwtAccessSecret" | "accessTokenTtlSeconds" | "refreshTokenTtlSeconds" | "passwordHashRounds" | "corsOrigins"
  > & { trustProxy?: number };
  users: UserRepository;
  refreshTokens: RefreshTokenRepository;
  projects: ProjectRepository;
  logger?: Logger;
  /** Replaces the login/register limiter; tests pass a no-op or a tight one. */
  credentialLimiter?: RequestHandler;
  now?: () => Date;
}

export function defaultCredentialLimiter(): RequestHandler {
  return rateLimit({
    windowMs: 15 * 60 * 1000,
    limit: 20,
    standardHeaders: "draft-8",
    legacyHeaders: false,
    handler: (_req, res) => {
      res.status(429).json({ error: { code: "RATE_LIMITED", message: "Too many attempts, try again later" } });
    },
  });
}

/**
 * Builds the Express app from its dependencies. Nothing here touches process.env
 * or opens a connection, so tests construct it with in-memory repositories.
 */
export function createApp(deps: AppDeps): Express {
  const logger = deps.logger ?? console;
  const authService = new AuthService(deps.users, deps.refreshTokens, deps.config, deps.now);
  const projectService = new ProjectService(deps.projects);

  const app = express();
  if (deps.config.trustProxy) app.set("trust proxy", deps.config.trustProxy);
  app.use(helmet());
  // The Android app is not a browser and ignores CORS; this only affects web clients.
  app.use(cors({ origin: deps.config.corsOrigins.length > 0 ? deps.config.corsOrigins : false }));
  app.use(express.json({ limit: "1mb" }));

  app.get("/health", (_req, res) => {
    res.json({ data: { status: "ok" } });
  });

  app.use(
    "/api/v1/auth",
    authRoutes(new AuthController(authService), deps.credentialLimiter ?? defaultCredentialLimiter()),
  );
  app.use("/api/v1/projects", projectRoutes(new ProjectsController(projectService), requireAuth(authService)));

  app.use(notFoundHandler);
  app.use(errorHandler(logger));
  return app;
}
