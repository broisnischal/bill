import { createFileRoute } from "@tanstack/react-router";
import * as z from "zod";

import { run, json, parseBody, requireAdmin, requireStore } from "#/lib/api/v1.ts";
import { createCreditNote } from "#/lib/invoice/service.ts";

const bodySchema = z.object({
  reason: z.string().trim().min(5, "Give the reason for the return").max(300),
});

/**
 * Reverses a bill. The credit note takes its number from the store's `CN-` series at the
 * moment it is raised, so this needs a connection: a return is worth waiting for network,
 * and a number drawn from a device block would put returns in the same series as sales.
 */
export const Route = createFileRoute("/api/v1/invoices/$invoiceId/credit-note")({
  server: {
    handlers: {
      POST: ({ request, params }) =>
        run(async () => {
          const context = await requireStore(request);
          requireAdmin(context);
          const { reason } = await parseBody(request, bodySchema);

          const creditNote = await createCreditNote({
            store: context.store,
            invoiceId: params.invoiceId,
            reason,
            actor: context.actor,
          });

          return json({ creditNote }, 201);
        }),
    },
  },
});
