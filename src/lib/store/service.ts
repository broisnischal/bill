import "@tanstack/react-start/server-only";
import { and, eq } from "drizzle-orm";

import { db } from "#/lib/db/index.ts";
import { store, storeMember } from "#/lib/db/schema/index.ts";
import { bsStringToAd, toAdDateString } from "#/lib/nepali/date.ts";

import type { StoreRegistrationInput } from "./schema";

/**
 * Store lookups used by server routes and middleware. Server-only, so nothing here can
 * be pulled into a client bundle by a route that happens to import a neighbour.
 */

/** The business this user bills for, with their role, or null if they have not registered one. */
export async function findStoreForUser(userId: string) {
  const [membership] = await db
    .select({ store, role: storeMember.role })
    .from(storeMember)
    .innerJoin(store, eq(store.id, storeMember.storeId))
    .where(eq(storeMember.userId, userId))
    .limit(1);
  return membership ?? null;
}

export async function assertStoreOwnership(storeId: string, userId: string) {
  const [membership] = await db
    .select({ role: storeMember.role })
    .from(storeMember)
    .where(and(eq(storeMember.storeId, storeId), eq(storeMember.userId, userId)))
    .limit(1);
  return membership ?? null;
}

/** Why a registration was refused, so the web form and the API can say the same thing. */
export class StoreRegistrationError extends Error {
  constructor(
    readonly code: "already_registered" | "pan_taken",
    message: string,
  ) {
    super(message);
    this.name = "StoreRegistrationError";
  }
}

/**
 * Registers a business and makes the caller its owner.
 *
 * The PAN is the taxpayer's identity, so it is unique across the platform: two accounts
 * cannot bill under the same PAN.
 */
export async function registerStore({
  userId,
  input,
}: {
  userId: string;
  input: StoreRegistrationInput;
}) {
  const existing = await findStoreForUser(userId);
  if (existing) {
    throw new StoreRegistrationError(
      "already_registered",
      "This account already has a registered business",
    );
  }

  const [taken] = await db.select({ id: store.id }).from(store).where(eq(store.pan, input.pan));
  if (taken) {
    throw new StoreRegistrationError(
      "pan_taken",
      `PAN ${input.pan} is already registered on this platform`,
    );
  }

  return db.transaction(async (tx) => {
    const [created] = await tx
      .insert(store)
      .values({
        ownerId: userId,
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
      })
      .returning();

    await tx.insert(storeMember).values({ storeId: created.id, userId, role: "owner" });

    return { store: created, role: "owner" as const };
  });
}
