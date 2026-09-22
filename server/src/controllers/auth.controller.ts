import type { Request, Response } from "express";
import { LoginSchema, RefreshSchema, RegisterSchema } from "../schemas/auth.schema.js";
import { parseOrThrow } from "../schemas/parse.js";
import type { AuthService } from "../services/auth.service.js";

/** Thin HTTP adapter: validate, delegate to the service, wrap in the envelope. */
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  register = async (req: Request, res: Response) => {
    const body = parseOrThrow(RegisterSchema, req.body);
    res.status(201).json({ data: await this.auth.register(body) });
  };

  login = async (req: Request, res: Response) => {
    const body = parseOrThrow(LoginSchema, req.body);
    res.json({ data: await this.auth.login(body) });
  };

  refresh = async (req: Request, res: Response) => {
    const body = parseOrThrow(RefreshSchema, req.body);
    res.json({ data: await this.auth.refresh(body.refreshToken) });
  };
}
