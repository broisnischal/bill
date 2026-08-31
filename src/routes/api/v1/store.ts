import { createFileRoute } from "@tanstack/react-router";

import { ApiError, run, json, parseBody, requireStore, requireUser } from "#/lib/api/v1.ts";
import { storeRegistrationSchema } from "#/lib/store/schema.ts";
import { registerStore, StoreRegistrationError } from "#/lib/store/service.ts";

/**
 * Registering the business is the one form a shopkeeper fills in before they can bill.
 * It is the same schema and the same service the web onboarding uses, so a business
 * registered on a phone is indistinguishable from one registered in a browser.
 */
export const Route = createFileRoute("/api/v1/store")({
  server: {
    handlers: {
      GET: ({ request }) =>
        run(async () => {
          const context = await requireStore(request);
          return json({ store: context.store, role: context.role });
        }),

      POST: ({ request }) =>
        run(async () => {
          const user = await requireUser(request);
          const input = await parseBody(request, storeRegistrationSchema);

          try {
            const registered = await registerStore({ userId: user.id, input });
            return json(registered, 201);
          } catch (error) {
            if (error instanceof StoreRegistrationError) {
              throw new ApiError(409, error.code, error.message);
            }
            throw error;
          }
        }),
    },
  },
});
