import { createFileRoute } from "@tanstack/react-router";
import { eq } from "drizzle-orm";

import { issueShopperLink, readCardCode } from "#/lib/api/cards.ts";
import { ApiError, json, requireStore, run } from "#/lib/api/v1.ts";
import { db } from "#/lib/db/index.ts";
import { shopperProfile } from "#/lib/db/schema/index.ts";

/**
 * Resolves the card a shopper held up at the counter.
 *
 * Two things have to hold. The scanned code must be current — cards rotate every few
 * minutes, so a photograph of one taken yesterday resolves to nothing. And the caller
 * must be a signed-in store, because a code anyone could resolve would turn a printed
 * card into a directory of phone numbers.
 *
 * What comes back includes a signed link. That is what the till keeps and what the bill
 * carries, so a shop that was offline for a fortnight can still say whose bill it was.
 */
export const Route = createFileRoute("/api/v1/profiles/$token")({
  server: {
    handlers: {
      GET: ({ request, params }) =>
        run(async () => {
          await requireStore(request);

          const token = readCardCode(params.token);
          if (!token) {
            throw new ApiError(410, "code_expired", "That card has expired. Ask for it again.");
          }

          const [profile] = await db
            .select({
              userId: shopperProfile.userId,
              name: shopperProfile.name,
              phone: shopperProfile.phone,
              pan: shopperProfile.pan,
              address: shopperProfile.address,
            })
            .from(shopperProfile)
            .where(eq(shopperProfile.token, token));

          if (!profile) throw new ApiError(404, "not_found", "That code did not match a customer");

          const { userId, ...details } = profile;
          return json({ profile: { ...details, link: issueShopperLink(userId) } });
        }),
    },
  },
});
