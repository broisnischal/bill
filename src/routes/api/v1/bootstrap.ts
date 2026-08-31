import { createFileRoute } from "@tanstack/react-router";

import { run, json, requireUser } from "#/lib/api/v1.ts";
import { fiscalYearFor, toBsString } from "#/lib/nepali/date.ts";
import { findStoreForUser } from "#/lib/store/service.ts";

/**
 * Everything the app needs on the first screen after sign-in, in one call: who you are,
 * whether a business is registered, and the server's idea of today. A phone on a slow
 * connection makes one request, not four.
 */
export const Route = createFileRoute("/api/v1/bootstrap")({
  server: {
    handlers: {
      GET: ({ request }) =>
        run(async () => {
          const user = await requireUser(request);
          const membership = await findStoreForUser(user.id);
          const now = new Date();

          return json({
            user: {
              id: user.id,
              name: user.name,
              phoneNumber: user.phoneNumber ?? null,
            },
            store: membership?.store ?? null,
            role: membership?.role ?? null,
            serverTime: now.toISOString(),
            fiscalYear: fiscalYearFor(now),
            miti: toBsString(now),
          });
        }),
    },
  },
});
