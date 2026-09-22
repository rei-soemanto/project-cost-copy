export const COST_ITEM_KINDS = ["JASA", "BARANG", "TRANSPORTASI", "LAIN_LAIN"] as const;
export type CostItemKind = (typeof COST_ITEM_KINDS)[number];

/** Business limits, enforced server-side regardless of what the client allows. */
export const MAX_TRANSPORTASI = 20;
export const MAX_ITEMS_PER_PROJECT = 500;

/**
 * One line item. All four kinds share this shape; see the table in API.md for
 * which fields each kind uses. Money (`amount`) is integer rupiah.
 */
export interface CostItem {
  id: string;
  kind: CostItemKind;
  description: string;
  engineer: string | null;
  quantity: number | null;
  amount: number;
}

export interface Project {
  id: string;
  ownerId: string;
  name: string;
  customer: string;
  pic: string;
  /** Integer rupiah. */
  hargaKontrak: number;
  items: CostItem[];
  createdAt: Date;
  updatedAt: Date;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  passwordHash: string;
  createdAt: Date;
}

/** The user as the API exposes it: never includes the password hash. */
export interface PublicUser {
  id: string;
  email: string;
  fullName: string;
}

export function toPublicUser(user: User): PublicUser {
  return { id: user.id, email: user.email, fullName: user.fullName };
}
