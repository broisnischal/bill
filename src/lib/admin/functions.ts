import { createServerFn } from "@tanstack/react-start";
import { setResponseStatus } from "@tanstack/react-start/server";
import { desc, eq, inArray } from "drizzle-orm";
import * as z from "zod";

import { authMiddleware } from "#/lib/auth/middleware.ts";
import { db } from "#/lib/db/index.ts";
import { store, storeDocument, user } from "#/lib/db/schema/index.ts";
import { approveStore, documentsFor, rejectStore, ReviewError } from "#/lib/store/review.ts";
import { storeReviewSchema } from "#/lib/store/schema.ts";

import { adminMiddleware, isAdminPhone } from "./middleware";

/**
 * Whether the signed-in account may review businesses.
 *
 * Behind plain auth rather than the admin middleware, because every account asks it in
 * order to decide whether to draw the link. It answers about the caller only.
 */
export const $isReviewer = createServerFn({ method: "GET" })
  .middleware([authMiddleware])
  .handler(({ context }) => isAdminPhone(context.user.phoneNumber));

/**
 * The review queue.
 *
 * Pending first and oldest first inside that, because the queue is a promise to someone
 * who cannot bill until it is kept, and the fair order is the order they arrived in.
 */
export const $reviewQueue = createServerFn({ method: "GET" })
  .middleware([adminMiddleware])
  .validator(
    z.object({ status: z.enum(["pending", "approved", "rejected", "all"]).default("pending") }),
  )
  .handler(async ({ data }) => {
    const rows = await db
      .select({
        store,
        ownerName: user.name,
        ownerPhone: user.phoneNumber,
      })
      .from(store)
      .innerJoin(user, eq(user.id, store.ownerId))
      .where(data.status === "all" ? undefined : eq(store.status, data.status))
      .orderBy(desc(store.createdAt))
      .limit(200);

    // One query for the papers of every store on the page rather than one per row.
    const ids = rows.map((row) => row.store.id);
    const papers = ids.length
      ? await db.select().from(storeDocument).where(inArray(storeDocument.storeId, ids))
      : [];

    return rows.map((row) => ({
      ...row,
      documents: papers.filter((paper) => paper.storeId === row.store.id),
    }));
  });

/** One business, everything a reviewer needs on the page at once. */
export const $storeForReview = createServerFn({ method: "GET" })
  .middleware([adminMiddleware])
  .validator(z.object({ storeId: z.string().min(1) }))
  .handler(async ({ data }) => {
    const [row] = await db
      .select({ store, ownerName: user.name, ownerPhone: user.phoneNumber })
      .from(store)
      .innerJoin(user, eq(user.id, store.ownerId))
      .where(eq(store.id, data.storeId));

    if (!row) {
      setResponseStatus(404);
      throw new Error("No such business");
    }

    return { ...row, documents: await documentsFor(data.storeId) };
  });

/**
 * Approves or refuses a business.
 *
 * Approving freezes the PAN, so it is the one action here that cannot be taken back by
 * editing a field: from this point the number is on printed bills and the series is
 * issued under it.
 */
export const $reviewStore = createServerFn({ method: "POST" })
  .middleware([adminMiddleware])
  .validator(storeReviewSchema)
  .handler(async ({ data, context }) => {
    try {
      return data.decision === "approved"
        ? await approveStore({ storeId: data.storeId, reviewerId: context.admin.id })
        : await rejectStore({
            storeId: data.storeId,
            reviewerId: context.admin.id,
            note: data.note,
          });
    } catch (error) {
      if (error instanceof ReviewError) {
        setResponseStatus(409);
        throw new Error(error.message);
      }
      throw error;
    }
  });
