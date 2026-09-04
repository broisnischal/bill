import "@tanstack/react-start/server-only";
import { and, eq } from "drizzle-orm";
import type * as z from "zod";

import { db } from "#/lib/db/index.ts";
import { customer, item } from "#/lib/db/schema/index.ts";
import type { customerSchema, itemSchema } from "#/lib/store/schema.ts";

/**
 * Saving a product or a buyer, from either the till or the browser.
 *
 * Both callers used to be one route that took a single record per request, and it read
 * an `id` as "update the row with this id" — 404 when there was none. The till always
 * sends an id, because it generated one to write the row locally before it had a network,
 * so the first push of a product created on a phone answered 404 every time: never
 * confirmed, never cleared, retried on every sync forever. The doc comment on that route
 * said it was an upsert. It is one now.
 */

/** The saved row, and the id it ended up under, which is not always the one that was sent. */
type Saved<T> = { row: T; id: string };

/**
 * A product.
 *
 * Three ways in, in order: the id the caller gave, a product of the same name this store
 * already sells, and finally a new row. The middle one is deliberate and predates this —
 * a till that typed "Masu" onto a bill rather than picking it sends a fresh id, and
 * inserting on that id gave the shop a second Masu at the same price on every bill. The
 * name is what a shopkeeper considers unique.
 *
 * Because of that, the id a row is saved under can differ from the one sent, which is why
 * the caller is told which it was.
 */
export async function upsertItem({
  storeId,
  value,
}: {
  storeId: string;
  value: z.infer<typeof itemSchema>;
}): Promise<Saved<typeof item.$inferSelect>> {
  const { id, ...values } = value;

  if (id) {
    const [updated] = await db
      .update(item)
      .set(values)
      .where(and(eq(item.id, id), eq(item.storeId, storeId)))
      .returning();
    if (updated) return { row: updated, id: updated.id };
  }

  const [byName] = await db
    .select()
    .from(item)
    .where(and(eq(item.storeId, storeId), eq(item.name, values.name)));

  if (byName) {
    const [merged] = await db.update(item).set(values).where(eq(item.id, byName.id)).returning();
    return { row: merged!, id: merged!.id };
  }

  // Insert under the id the caller sent when there is one, so a bill already on the till
  // pointing at this product still points at it once both sides have the row.
  const [created] = await db
    .insert(item)
    .values({ ...values, storeId, ...(id ? { id } : {}) })
    .returning();
  return { row: created!, id: created!.id };
}

/**
 * A buyer.
 *
 * No name matching here, unlike a product: two customers called Ram are two customers,
 * and merging them would put one shop's debt against another person.
 */
export async function upsertCustomer({
  storeId,
  value,
}: {
  storeId: string;
  value: z.infer<typeof customerSchema>;
}): Promise<Saved<typeof customer.$inferSelect>> {
  const { id, ...values } = value;

  if (id) {
    const [updated] = await db
      .update(customer)
      .set(values)
      .where(and(eq(customer.id, id), eq(customer.storeId, storeId)))
      .returning();
    if (updated) return { row: updated, id: updated.id };
  }

  const [created] = await db
    .insert(customer)
    .values({ ...values, storeId, ...(id ? { id } : {}) })
    .returning();
  return { row: created!, id: created!.id };
}
