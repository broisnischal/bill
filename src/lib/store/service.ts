import "@tanstack/react-start/server-only";
import { and, eq } from "drizzle-orm";

import { db } from "#/lib/db/index.ts";
import { store, storeMember } from "#/lib/db/schema/index.ts";

/**
 * Store lookups used by server routes and middleware. Server-only, so nothing here can
 * be pulled into a client bundle by a route that happens to import a neighbour.
 */

/** The business this user bills for, with their role, or null if they have not registered one. */
export async function findStoreForUser(userId: string) {
  const [membership] = await db
    .select({ store, role: storeMember.role })
    .from(storeMember)
    .innerJoin(store, eq(store.id, storeMember.storeId))
    .where(eq(storeMember.userId, userId))
    .limit(1);
  return membership ?? null;
}

export async function assertStoreOwnership(storeId: string, userId: string) {
  const [membership] = await db
    .select({ role: storeMember.role })
    .from(storeMember)
    .where(and(eq(storeMember.storeId, storeId), eq(storeMember.userId, userId)))
    .limit(1);
  return membership ?? null;
}
