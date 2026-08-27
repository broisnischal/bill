import { createMiddleware } from "@tanstack/react-start";
import { setResponseStatus } from "@tanstack/react-start/server";
import { eq } from "drizzle-orm";

import { authMiddleware } from "#/lib/auth/middleware.ts";
import { db } from "#/lib/db/index.ts";
import { store, storeMember } from "#/lib/db/schema/index.ts";

/**
 * Resolves the caller's store and puts it in context. Everything that touches billing
 * data goes through here, so a request can never reach another taxpayer's invoices by
 * passing a different store id.
 */
export const storeMiddleware = createMiddleware()
  .middleware([authMiddleware])
  .server(async ({ next, context }) => {
    const [membership] = await db
      .select({ store, role: storeMember.role })
      .from(storeMember)
      .innerJoin(store, eq(store.id, storeMember.storeId))
      .where(eq(storeMember.userId, context.user.id))
      .limit(1);

    if (!membership) {
      setResponseStatus(403);
      throw new Error("No store registered for this account");
    }

    return next({ context: { store: membership.store, role: membership.role } });
  });

/** Owner or manager only: settings, cancellations, credit notes. */
export const storeAdminMiddleware = createMiddleware()
  .middleware([storeMiddleware])
  .server(async ({ next, context }) => {
    if (context.role !== "owner" && context.role !== "manager") {
      setResponseStatus(403);
      throw new Error("This action needs owner or manager access");
    }
    return next();
  });
