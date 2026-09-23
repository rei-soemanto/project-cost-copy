import { toPublicUser, type PublicUser } from "../domain/types.js";
import { AppError } from "../errors/AppError.js";
import type { UserRepository } from "../repositories/types.js";

export interface Profile extends PublicUser {
  isAdmin: boolean;
}

/** The signed-in user's own account, including whether they are an admin. */
export class AccountService {
  private readonly adminEmails: Set<string>;

  constructor(
    private readonly users: UserRepository,
    adminEmails: readonly string[],
  ) {
    this.adminEmails = new Set(adminEmails.map((e) => e.toLowerCase()));
  }

  /**
   * Admin status comes only from the server's ADMIN_EMAILS setting, checked on
   * every request, so removing someone from the list takes effect immediately.
   */
  async profile(userId: string): Promise<Profile> {
    const user = await this.users.findById(userId);
    // A valid token for an account that no longer exists.
    if (!user) throw AppError.unauthorized();
    return { ...toPublicUser(user), isAdmin: this.adminEmails.has(user.email.toLowerCase()) };
  }
}
