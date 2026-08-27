import { afterAll, beforeAll, describe, expect, it } from "vite-plus/test";

/**
 * Exercises the billing service against a real Postgres and a real S3 bucket, because
 * the parts that matter here (gapless numbering under concurrency, the audit trail, the
 * archived PDF) only exist at the database and storage boundary.
 *
 * Needs `docker compose up -d`. Skips itself when nothing is listening.
 */

process.loadEnvFile?.(".env");

type Loaded = Awaited<ReturnType<typeof load>>;

async function load() {
  const [{ db }, schema, service, storage] = await Promise.all([
    import("#/lib/db/index.ts"),
    import("#/lib/db/schema/index.ts"),
    import("./service.ts"),
    import("#/lib/storage/s3.ts"),
  ]);
  return { db, schema, service, storage };
}

const suffix = Math.floor(Number(process.env.TEST_SEED ?? "0")) || 1;

let ctx: Loaded | undefined;
let storeId: string | undefined;
let userId: string | undefined;
let reachable = true;

beforeAll(async () => {
  try {
    ctx = await load();
    const { db, schema } = ctx;
    userId = `test-user-${suffix}-${Date.now()}`;
    await db.insert(schema.user).values({
      id: userId,
      name: "Test Biller",
      email: `${userId}@example.test`,
    });
    const [store] = await db
      .insert(schema.store)
      .values({
        ownerId: userId,
        name: "Integration Traders",
        pan: String(100_000_000 + Math.floor(Math.random() * 899_999_999)),
        taxpayerType: "vat",
        registrationDate: "2020-07-16",
        registrationDateBs: "2077-04-01",
        address: "Test Marg",
        district: "Kathmandu",
      })
      .returning();
    storeId = store.id;
    await db.insert(schema.storeMember).values({ storeId: store.id, userId, role: "owner" });
  } catch (error) {
    reachable = false;
    console.warn(`Skipping integration test: ${(error as Error).message}`);
  }
}, 60_000);

afterAll(async () => {
  if (!ctx || !storeId || !userId) return;
  const { db, schema } = ctx;
  const { eq, inArray } = await import("drizzle-orm");
  const invoices = await db
    .select({ id: schema.invoice.id })
    .from(schema.invoice)
    .where(eq(schema.invoice.storeId, storeId));
  const ids = invoices.map((row) => row.id);
  if (ids.length) {
    await db.delete(schema.invoiceAudit).where(inArray(schema.invoiceAudit.invoiceId, ids));
    await db.delete(schema.invoiceItem).where(inArray(schema.invoiceItem.invoiceId, ids));
    await db.delete(schema.invoice).where(inArray(schema.invoice.id, ids));
  }
  await db.delete(schema.invoiceCounter).where(eq(schema.invoiceCounter.storeId, storeId));
  await db.delete(schema.storeMember).where(eq(schema.storeMember.storeId, storeId));
  await db.delete(schema.store).where(eq(schema.store.id, storeId));
  await db.delete(schema.user).where(eq(schema.user.id, userId));
}, 60_000);

describe("billing service", () => {
  it(
    "numbers concurrent bills sequentially, archives each PDF and keeps an audit trail",
    { timeout: 120_000 },
    async () => {
      if (!reachable || !ctx || !storeId || !userId) return;
      const { db, schema, service, storage } = ctx;
      const { eq } = await import("drizzle-orm");

      const [store] = await db.select().from(schema.store).where(eq(schema.store.id, storeId));
      const actor = { id: userId, name: "Test Biller" };
      const line = {
        description: "Integration widget",
        unit: "pcs",
        quantityMilli: 2000,
        unitPricePaisa: 50_000,
        discountPaisa: 0,
        vatApplicable: true,
      };

      // Five tills billing at once must still produce 1..5 with nothing skipped.
      const created = await Promise.all(
        Array.from({ length: 5 }, (_, index) =>
          service.createInvoice({
            store,
            actor,
            input: {
              invoiceType: "tax_invoice",
              buyerName: `Buyer ${index + 1}`,
              paymentMethod: "cash",
              discountPaisa: 0,
              saveCustomer: false,
              lines: [line],
            },
          }),
        ),
      );

      const sequences = created.map((invoice) => invoice.sequence).sort((a, b) => a - b);
      expect(sequences).toEqual([1, 2, 3, 4, 5]);
      expect(new Set(created.map((invoice) => invoice.invoiceNumber)).size).toBe(5);

      const first = created[0];
      expect(first.totalPaisa).toBe(100_000 + 13_000);
      expect(first.vatAmountPaisa).toBe(13_000);
      expect(first.pdfKey).toBeTruthy();
      expect(first.pdfSha256).toMatch(/^[0-9a-f]{64}$/);

      const archived = await storage.getPdf(first.pdfKey!);
      expect(new TextDecoder().decode(archived.slice(0, 5))).toBe("%PDF-");

      // Printing marks the copy number and lands in the trail.
      const printed = await service.registerPrint({
        storeId,
        invoiceId: first.id,
        format: "thermal80",
        actor,
      });
      expect(printed?.printCount).toBe(1);
      const reprinted = await service.registerPrint({
        storeId,
        invoiceId: first.id,
        format: "a4",
        actor,
      });
      expect(reprinted?.printCount).toBe(2);

      // A mistake is cancelled with a reason, never deleted.
      const cancelled = await service.cancelInvoice({
        store,
        invoiceId: created[1].id,
        reason: "Wrong quantity billed",
        actor,
      });
      expect(cancelled.status).toBe("cancelled");
      expect(cancelled.reason).toBe("Wrong quantity billed");
      await expect(
        service.cancelInvoice({
          store,
          invoiceId: created[1].id,
          reason: "Trying twice",
          actor,
        }),
      ).rejects.toThrow(/already cancelled/);

      // Or reversed with a credit note, which gets its own series.
      const note = await service.createCreditNote({
        store,
        invoiceId: created[2].id,
        reason: "Goods returned by the buyer",
        actor,
      });
      expect(note.invoiceType).toBe("credit_note");
      expect(note.sequence).toBe(1);
      expect(note.refInvoiceNumber).toBe(created[2].invoiceNumber);
      expect(note.totalPaisa).toBe(created[2].totalPaisa);

      const audits = await db
        .select()
        .from(schema.invoiceAudit)
        .where(eq(schema.invoiceAudit.invoiceId, first.id));
      const actions = audits.map((entry) => entry.action);
      expect(actions).toContain("invoice_created");
      expect(actions).toContain("pdf_archived");
      expect(actions).toContain("invoice_printed");
      expect(actions).toContain("invoice_reprinted");
    },
  );

  it("refuses an abbreviated invoice above the Rule 18 limit", { timeout: 60_000 }, async () => {
    if (!reachable || !ctx || !storeId || !userId) return;
    const { db, schema, service } = ctx;
    const { eq } = await import("drizzle-orm");
    const [store] = await db.select().from(schema.store).where(eq(schema.store.id, storeId));

    await expect(
      service.createInvoice({
        store,
        actor: { id: userId, name: "Test Biller" },
        input: {
          invoiceType: "abbreviated_tax_invoice",
          buyerName: "Walk-in",
          paymentMethod: "cash",
          discountPaisa: 0,
          saveCustomer: false,
          lines: [
            {
              description: "Expensive thing",
              unit: "pcs",
              quantityMilli: 1000,
              unitPricePaisa: 2_000_000,
              discountPaisa: 0,
              vatApplicable: true,
            },
          ],
        },
      }),
    ).rejects.toThrow(/cannot exceed NPR 10,000/);
  });
});
