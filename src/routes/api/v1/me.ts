import { createFileRoute } from "@tanstack/react-router";
import { eq } from "drizzle-orm";
import * as z from "zod";

import { cardCode } from "#/lib/api/cards.ts";
import { json, parseBody, requireUser, run } from "#/lib/api/v1.ts";
import { db } from "#/lib/db/index.ts";
import { shopperProfile } from "#/lib/db/schema/index.ts";
import { optionalPanSchema } from "#/lib/nepali/validators.ts";

const profileSchema = z.object({
  name: z.string().trim().min(1, "Your name is needed on a bill").max(120),
  phone: z.string().trim().max(30).optional(),
  pan: optionalPanSchema,
  address: z.string().trim().max(200).optional(),
});

/**
 * A shopper's own card, shown as a QR in customer mode so a shop can bill them without
 * asking for a name and a number. Reading it creates one on first use from what the
 * account already knows.
 */
export const Route = createFileRoute("/api/v1/me")({
  server: {
    handlers: {
      GET: ({ request }) =>
        run(async () => {
          const user = await requireUser(request);

          const [existing] = await db
            .select()
            .from(shopperProfile)
            .where(eq(shopperProfile.userId, user.id));
          if (existing) return json({ profile: existing, card: cardCode(existing.token) });

          const [created] = await db
            .insert(shopperProfile)
            .values({
              userId: user.id,
              // The account name is the phone number until the shopper sets a real one.
              name: user.name,
              phone: user.phoneNumber ?? null,
            })
            .returning();
          return json({ profile: created, card: cardCode(created.token) }, 201);
        }),

      POST: ({ request }) =>
        run(async () => {
          const user = await requireUser(request);
          const input = await parseBody(request, profileSchema);

          const [saved] = await db
            .insert(shopperProfile)
            .values({ userId: user.id, ...input })
            .onConflictDoUpdate({ target: shopperProfile.userId, set: input })
            .returning();
          return json({ profile: saved, card: cardCode(saved.token) });
        }),
    },
  },
});
