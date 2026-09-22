import { z } from "zod";
import { COST_ITEM_KINDS, MAX_ITEMS_PER_PROJECT, MAX_TRANSPORTASI, type CostItem } from "../domain/types.js";

/** Client-generated ids: short, URL-safe. */
export const Id = z
  .string()
  .trim()
  .min(1)
  .max(64)
  .regex(/^[A-Za-z0-9_-]+$/, "Id may contain only letters, digits, '-' and '_'");

/** Integer rupiah. Rejects fractions and anything a JS number cannot hold exactly. */
const Money = z.number().int("Amounts must be whole rupiah").nonnegative().max(Number.MAX_SAFE_INTEGER);

/**
 * One cost item. Input is lenient about fields that do not apply to the item's
 * kind, and the transform canonicalises them (e.g. `quantity` is always null for
 * JASA), so stored rows always match the table in API.md.
 */
const CostItemSchema = z
  .object({
    id: Id,
    kind: z.enum(COST_ITEM_KINDS),
    description: z.string().max(500).default(""),
    engineer: z.string().max(200).nullish(),
    quantity: z.number().int().nonnegative().max(1_000_000_000).nullish(),
    amount: Money,
  })
  .transform(
    (item): CostItem => ({
      id: item.id,
      kind: item.kind,
      description: item.description,
      engineer: item.kind === "JASA" ? (item.engineer ?? "") : null,
      quantity: item.kind === "BARANG" ? (item.quantity ?? 0) : null,
      amount: item.amount,
    }),
  );

export const ItemsSchema = z
  .array(CostItemSchema)
  .max(MAX_ITEMS_PER_PROJECT)
  .superRefine((items, ctx) => {
    const transportasi = items.filter((i) => i.kind === "TRANSPORTASI").length;
    if (transportasi > MAX_TRANSPORTASI) {
      ctx.addIssue({ code: "custom", message: `At most ${MAX_TRANSPORTASI} TRANSPORTASI items are allowed` });
    }
    const seen = new Set<string>();
    items.forEach((item, index) => {
      if (seen.has(item.id)) {
        ctx.addIssue({ code: "custom", message: "Duplicate item id", path: [index, "id"] });
      }
      seen.add(item.id);
    });
  });

const Name = z.string().trim().min(1, "Name is required").max(200);
const OptionalText = z.string().trim().max(200);

export const CreateProjectSchema = z.object({
  id: Id.optional(),
  name: Name,
  customer: OptionalText.default(""),
  pic: OptionalText.default(""),
  hargaKontrak: Money.default(0),
  items: ItemsSchema.default([]),
});

export const UpdateProjectSchema = z.object({
  name: Name.optional(),
  customer: OptionalText.optional(),
  pic: OptionalText.optional(),
  hargaKontrak: Money.optional(),
});

export const ReplaceItemsSchema = z.object({
  items: ItemsSchema,
});

export const ProjectParamsSchema = z.object({
  id: Id,
});
