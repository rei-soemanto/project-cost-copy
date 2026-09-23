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

/**
 * An item's contribution to the project total, in rupiah. BARANG is quantity x
 * unit price; every other kind is its amount.
 *
 * Must match the app exactly (composeApp domain/model/Project.kt): Barang.total
 * = quantity * hargaSatuan, the others use their amount. The export is the only
 * place the server computes totals, and a mismatch would make the backup
 * disagree with what users saw on screen.
 */
export function itemSubtotal(item: CostItem): number {
  return item.kind === "BARANG" ? (item.quantity ?? 0) * item.amount : item.amount;
}

export interface ProjectTotals {
  jasa: number;
  barang: number;
  transportasi: number;
  lainLain: number;
  total: number;
  /** Contract minus total cost. Negative means the costs exceed the contract. */
  sisaKontrak: number;
}

export function projectTotals(project: Pick<Project, "items" | "hargaKontrak">): ProjectTotals {
  const sumOf = (kind: CostItemKind) =>
    project.items.filter((i) => i.kind === kind).reduce((sum, i) => sum + itemSubtotal(i), 0);
  const jasa = sumOf("JASA");
  const barang = sumOf("BARANG");
  const transportasi = sumOf("TRANSPORTASI");
  const lainLain = sumOf("LAIN_LAIN");
  const total = jasa + barang + transportasi + lainLain;
  return { jasa, barang, transportasi, lainLain, total, sisaKontrak: project.hargaKontrak - total };
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
