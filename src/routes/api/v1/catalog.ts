import { createFileRoute } from "@tanstack/react-router";
import { and, eq } from "drizzle-orm";
import * as z from "zod";

import { ApiError, run, json, parseBody, requireStore } from "#/lib/api/v1.ts";
import { db } from "#/lib/db/index.ts";
import { customer, item } from "#/lib/db/schema/index.ts";
import { customerSchema, itemSchema } from "#/lib/store/schema.ts";

const bodySchema = z.discriminatedUnion("kind", [
  z.object({ kind: z.literal("item"), value: itemSchema }),
  z.object({ kind: z.literal("customer"), value: customerSchema }),
]);

/**
 * Saves an item or a buyer. Sending an `id` updates that row, so the app can push a
 * locally created record and a later edit of it through the same endpoint.
 */
export const Route = createFileRoute("/api/v1/catalog")({
  server: {
    handlers: {
      POST: ({ request }) =>
        run(async () => {
          const context = await requireStore(request);
          const body = await parseBody(request, bodySchema);
          const storeId = context.store.id;

          if (body.kind === "item") {
            const { id, ...values } = body.value;
            if (id) {
              const [updated] = await db
                .update(item)
                .set(values)
                .where(and(eq(item.id, id), eq(item.storeId, storeId)))
                .returning();
              if (!updated) throw new ApiError(404, "not_found", "That item is not in this store");
              return json({ item: updated });
            }
            const [created] = await db
              .insert(item)
              .values({ ...values, storeId })
              .returning();
            return json({ item: created }, 201);
          }

          const { id, ...values } = body.value;
          if (id) {
            const [updated] = await db
              .update(customer)
              .set(values)
              .where(and(eq(customer.id, id), eq(customer.storeId, storeId)))
              .returning();
            if (!updated)
              throw new ApiError(404, "not_found", "That customer is not in this store");
            return json({ customer: updated });
          }
          const [created] = await db
            .insert(customer)
            .values({ ...values, storeId })
            .returning();
          return json({ customer: created }, 201);
        }),
    },
  },
});
