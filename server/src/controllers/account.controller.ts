import type { Request, Response } from "express";
import { currentUserId } from "../middleware/auth.js";
import type { AccountService } from "../services/account.service.js";

export class AccountController {
  constructor(private readonly accounts: AccountService) {}

  /** The caller's profile. The app uses isAdmin to decide whether to offer the full export. */
  me = async (req: Request, res: Response) => {
    res.json({ data: await this.accounts.profile(currentUserId(req)) });
  };
}
