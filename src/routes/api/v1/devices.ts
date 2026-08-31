import { createFileRoute } from "@tanstack/react-router";
import * as z from "zod";

import { run, json, parseBody, requireStore } from "#/lib/api/v1.ts";
import { upsertDevice } from "#/lib/invoice/lease.ts";

const registerDeviceSchema = z.object({
  /** Generated on the device at first launch and kept for the life of the install. */
  id: z.uuid(),
  name: z.string().trim().min(1).max(80),
  platform: z.enum(["android", "ios"]),
  appVersion: z.string().trim().max(40).optional(),
  pushToken: z.string().trim().max(500).optional(),
});

/**
 * A till announces itself before it can be handed numbers to print offline. Re-registering
 * is safe and is how the app refreshes its push token or its name.
 */
export const Route = createFileRoute("/api/v1/devices")({
  server: {
    handlers: {
      POST: ({ request }) =>
        run(async () => {
          const context = await requireStore(request);
          const input = await parseBody(request, registerDeviceSchema);

          const registered = await upsertDevice({
            ...input,
            storeId: context.store.id,
            userId: context.user.id,
          });

          return json({ device: registered });
        }),
    },
  },
});
