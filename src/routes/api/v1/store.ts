import { createFileRoute } from "@tanstack/react-router";
import { and, eq } from "drizzle-orm";

import { ApiError, run, json, parseBody, requireStore, requireUser } from "#/lib/api/v1.ts";
import { db } from "#/lib/db/index.ts";
import { store as storeTable } from "#/lib/db/schema/index.ts";
import { encryptSecret } from "#/lib/ird/credentials.ts";
import { bsStringToAd, toAdDateString } from "#/lib/nepali/date.ts";
import { assertPanEditable, ReviewError } from "#/lib/store/review.ts";
import { storeRegistrationSchema, storeSettingsSchema } from "#/lib/store/schema.ts";
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

      /**
       * Editing the business from the app.
       *
       * The PAN is the one field that can stop being editable: once the business is
       * approved it is on printed bills and the number series is issued under it, so
       * changing it would leave a register whose earlier pages belong to somebody else.
       */
      PATCH: ({ request }) =>
        run(async () => {
          const context = await requireStore(request);
          if (context.role !== "owner" && context.role !== "manager") {
            throw new ApiError(403, "forbidden", "This needs owner or manager access");
          }

          const input = await parseBody(request, storeSettingsSchema);

          try {
            assertPanEditable(context.store, input.pan);
          } catch (error) {
            if (error instanceof ReviewError) throw new ApiError(409, error.code, error.message);
            throw error;
          }

          const [updated] = await db
            .update(storeTable)
            .set({
              name: input.name,
              nameNepali: input.nameNepali,
              tradeName: input.tradeName,
              pan: input.pan,
              taxpayerType: input.taxpayerType,
              registrationDate: toAdDateString(bsStringToAd(input.registrationDateBs)),
              registrationDateBs: input.registrationDateBs,
              registrationNumber: input.registrationNumber,
              businessType: input.businessType,
              taxOffice: input.taxOffice,
              address: input.address,
              ward: input.ward,
              municipality: input.municipality,
              district: input.district,
              province: input.province,
              phone: input.phone,
              email: input.email,
              website: input.website,
              invoicePrefix: input.invoicePrefix,
              printFooterNote: input.printFooterNote,
              bankDetails: input.bankDetails,
              cbmsEnabled: input.cbmsEnabled,
              cbmsUsername: input.cbmsUsername,
              // Blank means "keep the stored one", which is the only way a field that is
              // never sent back to the client can be left alone.
              ...(input.cbmsPassword
                ? { cbmsPasswordEncrypted: encryptSecret(input.cbmsPassword) }
                : {}),
            })
            .where(and(eq(storeTable.id, context.store.id)))
            .returning();

          return json({ store: updated, role: context.role });
        }),
    },
  },
});
