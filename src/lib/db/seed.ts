/**
 * A shop you can bill from immediately.
 *
 * Getting to a usable state by hand means an OTP, a registration form and a catalogue
 * before you can look at the screen you are actually working on. This creates all of it
 * in one command, and refuses to run against anything but a local database so it cannot
 * be pointed at real data by accident.
 *
 * Run with `bun run seed`. It is idempotent: the same PAN is reused rather than
 * duplicated, so running it twice does nothing the second time.
 */
import { eq } from "drizzle-orm";

import { env } from "#/env/server.ts";
import { db } from "#/lib/db/index.ts";
import { customer, item, store, storeMember, user } from "#/lib/db/schema/index.ts";
import { bsStringToAd, toAdDateString } from "#/lib/nepali/date.ts";

const SEED_PHONE = "+9779800000000";
const SEED_PAN = "300000001";

async function seed() {
  const host = new URL(env.DATABASE_URL).hostname;
  if (host !== "localhost" && host !== "127.0.0.1") {
    throw new Error(`Refusing to seed a database that is not local: ${host}`);
  }

  const [existing] = await db.select().from(store).where(eq(store.pan, SEED_PAN));
  if (existing) {
    console.info(`Seed store already present: ${existing.name} (PAN ${existing.pan})`);
    console.info(`Sign in as ${SEED_PHONE}; the OTP is logged by the dev server.`);
    return;
  }

  const [owner] = await db
    .insert(user)
    .values({
      id: crypto.randomUUID(),
      name: "Seed Shopkeeper",
      email: `${SEED_PHONE.replace(/\D/g, "")}@phone.bill.np`,
      emailVerified: false,
      phoneNumber: SEED_PHONE,
      phoneNumberVerified: true,
    })
    .onConflictDoNothing()
    .returning();

  const ownerId =
    owner?.id ?? (await db.select().from(user).where(eq(user.phoneNumber, SEED_PHONE)))[0]!.id;

  const [created] = await db
    .insert(store)
    .values({
      ownerId,
      name: "Annapurna Kirana Store",
      nameNepali: "अन्नपूर्ण किराना स्टोर",
      pan: SEED_PAN,
      taxpayerType: "vat",
      registrationDate: toAdDateString(bsStringToAd("2078-03-15")),
      registrationDateBs: "2078-03-15",
      businessType: "sole_proprietorship",
      taxOffice: "IRO Kathmandu 2",
      address: "New Baneshwor, Kathmandu",
      ward: 10,
      municipality: "Kathmandu Metropolitan City",
      district: "Kathmandu",
      province: "Bagmati",
      phone: "9800000000",
      printFooterNote: "Goods once sold are not returnable without this bill.",
    })
    .returning();

  await db.insert(storeMember).values({ storeId: created.id, userId: ownerId, role: "owner" });

  // A spread that exercises the parts of a bill that are easy to get wrong: an exempt
  // line, a price that does not divide evenly, and a quantity sold by weight.
  await db.insert(item).values([
    {
      storeId: created.id,
      name: "Basmati rice",
      unit: "kg",
      unitPricePaisa: 18_500,
      hsCode: "1006.30",
      vatApplicable: true,
    },
    {
      storeId: created.id,
      name: "Sunflower oil 1L",
      unit: "pcs",
      unitPricePaisa: 32_500,
      hsCode: "1512.11",
      vatApplicable: true,
    },
    {
      storeId: created.id,
      name: "Fresh milk 500ml",
      unit: "pcs",
      unitPricePaisa: 5_500,
      vatApplicable: false,
    },
    {
      storeId: created.id,
      name: "Wheat flour",
      unit: "kg",
      unitPricePaisa: 8_333,
      vatApplicable: false,
    },
    {
      storeId: created.id,
      name: "Detergent bar",
      unit: "pcs",
      unitPricePaisa: 4_500,
      vatApplicable: true,
    },
  ]);

  await db.insert(customer).values([
    { storeId: created.id, name: "Sita Sharma", phone: "9841000001" },
    {
      storeId: created.id,
      name: "Bikash Traders",
      pan: "301234999",
      phone: "9851000002",
      address: "Kalimati, Kathmandu",
    },
  ]);

  console.info(`Seeded "${created.name}" (PAN ${created.pan}) with 5 items and 2 customers.`);
  console.info(`Sign in as ${SEED_PHONE}; the OTP is logged by the dev server.`);
}

await seed();
process.exit(0);
