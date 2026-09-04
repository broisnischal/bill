import { createFileRoute } from "@tanstack/react-router";
import * as z from "zod";

import { run, json, parseBody, requireStore } from "#/lib/api/v1.ts";
import { upsertCustomer, upsertItem } from "#/lib/store/catalog.ts";
import { customerSchema, itemSchema } from "#/lib/store/schema.ts";

const bodySchema = z.discriminatedUnion("kind", [
  z.object({ kind: z.literal("item"), value: itemSchema }),
  z.object({ kind: z.literal("customer"), value: customerSchema }),
]);

/**
 * Saves an item or a buyer, one at a time.
 *
 * The browser's route. A till pushes its catalogue inside the sync request instead, so it
 * pays one round trip for a hundred records rather than a hundred; both go through the
 * same upsert in lib/store/catalog.ts.
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
            const { row } = await upsertItem({ storeId, value: body.value });
            return json({ item: row });
          }

          const { row } = await upsertCustomer({ storeId, value: body.value });
          return json({ customer: row });
        }),
    },
  },
});
