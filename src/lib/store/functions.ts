import { createServerFn } from "@tanstack/react-start";
import { setResponseStatus } from "@tanstack/react-start/server";
import { and, asc, desc, eq, ilike, or } from "drizzle-orm";
import * as z from "zod";

import { authMiddleware } from "#/lib/auth/middleware.ts";
import { db } from "#/lib/db/index.ts";
import { customer, item, store } from "#/lib/db/schema/index.ts";
import { encryptSecret } from "#/lib/ird/credentials.ts";
import { bsStringToAd, toAdDateString } from "#/lib/nepali/date.ts";

import { storeAdminMiddleware, storeMiddleware } from "./middleware";
import { customerSchema, itemSchema, storeRegistrationSchema, storeSettingsSchema } from "./schema";
import { findStoreForUser, registerStore, StoreRegistrationError } from "./service";

/** The caller's business, or null when they still have to register one. */
export const $getMyStore = createServerFn({ method: "GET" })
  .middleware([authMiddleware])
  .handler(async ({ context }) => findStoreForUser(context.user.id));

/**
 * Registers a business. The PAN is the taxpayer's identity, so it is unique across the
 * platform: two accounts cannot bill under the same PAN.
 */
export const $createStore = createServerFn({ method: "POST" })
  .middleware([authMiddleware])
  .validator(storeRegistrationSchema)
  .handler(async ({ data, context }) => {
    try {
      return await registerStore({ userId: context.user.id, input: data });
    } catch (error) {
      if (error instanceof StoreRegistrationError) {
        setResponseStatus(409);
        throw new Error(error.message);
      }
      throw error;
    }
  });

export const $updateStore = createServerFn({ method: "POST" })
  .middleware([storeAdminMiddleware])
  .validator(storeSettingsSchema)
  .handler(async ({ data, context }) => {
    const [updated] = await db
      .update(store)
      .set({
        name: data.name,
        nameNepali: data.nameNepali,
        tradeName: data.tradeName,
        taxpayerType: data.taxpayerType,
        registrationDate: toAdDateString(bsStringToAd(data.registrationDateBs)),
        registrationDateBs: data.registrationDateBs,
        registrationNumber: data.registrationNumber,
        businessType: data.businessType,
        taxOffice: data.taxOffice,
        address: data.address,
        ward: data.ward,
        municipality: data.municipality,
        district: data.district,
        province: data.province,
        phone: data.phone,
        email: data.email,
        website: data.website,
        invoicePrefix: data.invoicePrefix,
        printFooterNote: data.printFooterNote,
        bankDetails: data.bankDetails,
        cbmsEnabled: data.cbmsEnabled,
        cbmsUsername: data.cbmsUsername,
        // An empty password field means "keep what is stored".
        ...(data.cbmsPassword ? { cbmsPasswordEncrypted: encryptSecret(data.cbmsPassword) } : {}),
      })
      .where(eq(store.id, context.store.id))
      .returning();

    // The PAN is deliberately not updatable: bills already carry it.
    return { store: updated, role: context.role };
  });

export const $listCustomers = createServerFn({ method: "GET" })
  .middleware([storeMiddleware])
  .validator(z.object({ search: z.string().trim().max(100).optional() }).optional())
  .handler(async ({ data, context }) => {
    const search = data?.search;
    return db
      .select()
      .from(customer)
      .where(
        search
          ? and(
              eq(customer.storeId, context.store.id),
              or(ilike(customer.name, `%${search}%`), ilike(customer.pan, `%${search}%`)),
            )
          : eq(customer.storeId, context.store.id),
      )
      .orderBy(asc(customer.name))
      .limit(200);
  });

export const $saveCustomer = createServerFn({ method: "POST" })
  .middleware([storeMiddleware])
  .validator(customerSchema)
  .handler(async ({ data, context }) => {
    if (data.id) {
      const [updated] = await db
        .update(customer)
        .set({
          name: data.name,
          pan: data.pan,
          address: data.address,
          phone: data.phone,
          email: data.email,
        })
        .where(and(eq(customer.id, data.id), eq(customer.storeId, context.store.id)))
        .returning();
      if (!updated) {
        setResponseStatus(404);
        throw new Error("Customer not found");
      }
      return updated;
    }

    const [created] = await db
      .insert(customer)
      .values({
        storeId: context.store.id,
        name: data.name,
        pan: data.pan,
        address: data.address,
        phone: data.phone,
        email: data.email,
      })
      .returning();
    return created;
  });

export const $listItems = createServerFn({ method: "GET" })
  .middleware([storeMiddleware])
  .validator(
    z
      .object({
        search: z.string().trim().max(100).optional(),
        includeInactive: z.boolean().default(false),
      })
      .optional(),
  )
  .handler(async ({ data, context }) => {
    const filters = [eq(item.storeId, context.store.id)];
    if (!data?.includeInactive) filters.push(eq(item.active, true));
    if (data?.search) filters.push(ilike(item.name, `%${data.search}%`));

    return db
      .select()
      .from(item)
      .where(and(...filters))
      .orderBy(asc(item.name))
      .limit(500);
  });

export const $saveItem = createServerFn({ method: "POST" })
  .middleware([storeMiddleware])
  .validator(itemSchema)
  .handler(async ({ data, context }) => {
    const values = {
      name: data.name,
      description: data.description,
      hsCode: data.hsCode,
      sku: data.sku,
      unit: data.unit,
      unitPricePaisa: data.unitPricePaisa,
      vatApplicable: data.vatApplicable,
      active: data.active,
    };

    if (data.id) {
      const [updated] = await db
        .update(item)
        .set(values)
        .where(and(eq(item.id, data.id), eq(item.storeId, context.store.id)))
        .returning();
      if (!updated) {
        setResponseStatus(404);
        throw new Error("Item not found");
      }
      return updated;
    }

    const [created] = await db
      .insert(item)
      .values({ ...values, storeId: context.store.id })
      .returning();
    return created;
  });

/** Recently billed customers, for the quick-pick list on the invoice form. */
export const $recentCustomers = createServerFn({ method: "GET" })
  .middleware([storeMiddleware])
  .handler(async ({ context }) =>
    db
      .select()
      .from(customer)
      .where(eq(customer.storeId, context.store.id))
      .orderBy(desc(customer.updatedAt))
      .limit(8),
  );
