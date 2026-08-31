/**
 * A shop you can bill from immediately.
 *
 * Getting to a usable state by hand means an OTP, a registration form and a catalogue
 * before you can look at the screen you are actually working on. This writes all of it
 * as SQL instead of executing it, because the database is now D1 and the only way into
 * a D1 is `wrangler d1 execute`. Emitting the statements also means the same seed can be
 * applied to the local database or the deployed one without the script knowing which.
 *
 * Run with `bun run seed > drizzle/seed.sql`, then
 * `wrangler d1 execute bill --remote --file drizzle/seed.sql`.
 *
 * Every row carries an id generated here rather than read back afterwards, so the whole
 * thing is a list of inserts with no round trip. It is idempotent: each insert ignores a
 * conflict, so applying it twice changes nothing the second time.
 */
import { drizzle } from "drizzle-orm/sqlite-proxy";

import { customer, item, store, storeMember, user } from "#/lib/db/schema/index.ts";
import { bsStringToAd, toAdDateString } from "#/lib/nepali/date.ts";

const SEED_PHONE = "+9779800000000";
const SEED_PAN = "300000001";

/** Stable ids, so re-running the seed targets the same rows rather than making new ones. */
const OWNER_ID = "seed-user-annapurna";
const STORE_ID = "seed-store-annapurna";

const statements: { sql: string; params: unknown[] }[] = [];

/**
 * A driver that records instead of connecting. Drizzle builds the same SQL it would send
 * to D1, and nothing here needs a database to be reachable.
 */
const db = drizzle(async (sql, params) => {
  statements.push({ sql, params });
  return { rows: [] };
});

function literal(value: unknown): string {
  if (value === null || value === undefined) return "NULL";
  if (typeof value === "number") return String(value);
  if (typeof value === "boolean") return value ? "1" : "0";
  if (value instanceof Date) return String(value.getTime());
  return `'${String(value).replaceAll("'", "''")}'`;
}

function emit() {
  for (const { sql, params } of statements) {
    let index = 0;
    const inlined = sql.replace(/\?/g, () => literal(params[index++]));
    console.log(`${inlined};`);
  }
}

const now = new Date();

db.insert(user)
  .values({
    id: OWNER_ID,
    name: "Seed Shopkeeper",
    email: `${SEED_PHONE.replace(/\D/g, "")}@phone.bill.np`,
    emailVerified: false,
    phoneNumber: SEED_PHONE,
    phoneNumberVerified: true,
    createdAt: now,
    updatedAt: now,
  })
  .onConflictDoNothing()
  .run();

db.insert(store)
  .values({
    id: STORE_ID,
    ownerId: OWNER_ID,
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
    createdAt: now,
    updatedAt: now,
  })
  .onConflictDoNothing()
  .run();

db.insert(storeMember)
  .values({
    id: "seed-member-owner",
    storeId: STORE_ID,
    userId: OWNER_ID,
    role: "owner",
    createdAt: now,
  })
  .onConflictDoNothing()
  .run();

// A spread that exercises the parts of a bill that are easy to get wrong: an exempt
// line, a price that does not divide evenly, and a quantity sold by weight.
const items = [
  {
    id: "seed-item-rice",
    name: "Basmati rice",
    unit: "kg",
    unitPricePaisa: 18_500,
    hsCode: "1006.30",
    vatApplicable: true,
  },
  {
    id: "seed-item-oil",
    name: "Sunflower oil 1L",
    unit: "pcs",
    unitPricePaisa: 32_500,
    hsCode: "1512.11",
    vatApplicable: true,
  },
  {
    id: "seed-item-milk",
    name: "Fresh milk 500ml",
    unit: "pcs",
    unitPricePaisa: 5_500,
    vatApplicable: false,
  },
  {
    id: "seed-item-flour",
    name: "Wheat flour",
    unit: "kg",
    unitPricePaisa: 8_333,
    vatApplicable: false,
  },
  {
    id: "seed-item-soap",
    name: "Detergent bar",
    unit: "pcs",
    unitPricePaisa: 4_500,
    vatApplicable: true,
  },
];

for (const row of items) {
  db.insert(item)
    .values({ ...row, storeId: STORE_ID, createdAt: now, updatedAt: now })
    .onConflictDoNothing()
    .run();
}

const customers = [
  { id: "seed-customer-sita", name: "Sita Sharma", phone: "9841000001" },
  {
    id: "seed-customer-bikash",
    name: "Bikash Traders",
    pan: "301234999",
    phone: "9851000002",
    address: "Kalimati, Kathmandu",
  },
];

for (const row of customers) {
  db.insert(customer)
    .values({ ...row, storeId: STORE_ID, createdAt: now, updatedAt: now })
    .onConflictDoNothing()
    .run();
}

// The builder queues asynchronously, so let the microtasks drain before printing.
await new Promise((resolve) => setTimeout(resolve, 0));
emit();
