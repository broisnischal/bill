import { createFileRoute } from "@tanstack/react-router";

import { json, run } from "#/lib/api/v1.ts";
import { androidRelease } from "#/lib/app/release.ts";

/**
 * The version check the Android app makes on launch.
 *
 * Unauthenticated on purpose: a phone whose token has expired still has to be able to
 * learn that it must update, and none of this is more private than the releases page it
 * points at.
 */
export const Route = createFileRoute("/api/v1/app/android")({
  server: {
    handlers: {
      GET: () =>
        run(async () => {
          const response = json(androidRelease);
          // Every launch asks, and the answer only changes when a release ships.
          response.headers.set("cache-control", "public, max-age=300");
          return response;
        }),
    },
  },
});
