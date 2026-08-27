import { describe, expect, it } from "vite-plus/test";

import { fiscalYearFor, toBsString } from "#/lib/nepali/date.ts";
import { amountInWords, formatPaisa } from "#/lib/nepali/money.ts";

import { computeInvoice } from "./calc";
import { buildInvoiceDocument } from "./document";
import { renderInvoicePdf } from "./pdf";

const issuedAt = new Date("2026-08-26T04:12:00Z");

const lines = [
  {
    description: "Himalayan Green Tea 250g",
    unit: "pkt",
    quantityMilli: 3000,
    unitPricePaisa: 45_000,
    discountPaisa: 0,
    vatApplicable: true,
    hsCode: "0902.10",
  },
  {
    description: "काठको टेबल (Wooden table)",
    unit: "pcs",
    quantityMilli: 1000,
    unitPricePaisa: 1_250_000,
    discountPaisa: 25_000,
    vatApplicable: true,
    hsCode: "9403.30",
  },
  {
    description: "Fresh milk 1L",
    unit: "ltr",
    quantityMilli: 12_000,
    unitPricePaisa: 11_000,
    discountPaisa: 0,
    vatApplicable: false,
  },
];

function fixture() {
  const totals = computeInvoice({ lines, invoiceDiscountPaisa: 5_000, vatRateBp: 1300 });
  const store = {
    id: "s1",
    ownerId: "u1",
    name: "Everest Traders Pvt. Ltd.",
    nameNepali: "सगरमाथा ट्रेडर्स प्रा.लि.",
    tradeName: null,
    pan: "301234567",
    taxpayerType: "vat" as const,
    registrationDate: "2019-04-15",
    registrationDateBs: "2076-01-02",
    registrationNumber: "129384/075/076",
    businessType: "private_limited" as const,
    taxOffice: "IRO Kathmandu 2",
    address: "Naxal, Bhatbhateni Marg",
    ward: 1,
    municipality: "Kathmandu Metropolitan City",
    district: "Kathmandu",
    province: "Bagmati",
    country: "Nepal",
    phone: "+977-1-4412345",
    email: "billing@everest.com.np",
    website: null,
    logoKey: null,
    invoicePrefix: "",
    vatRateBp: 1300,
    printFooterNote: null,
    bankDetails: "Nabil Bank · A/C 0123456789012",
    cbmsEnabled: true,
    cbmsUsername: "everest",
    cbmsPasswordEncrypted: null,
    createdAt: issuedAt,
    updatedAt: issuedAt,
  };
  const invoice = {
    id: "i1",
    storeId: "s1",
    fiscalYear: fiscalYearFor(issuedAt),
    invoiceType: "tax_invoice" as const,
    sequence: 42,
    invoiceNumber: `${fiscalYearFor(issuedAt)}-000042`,
    refInvoiceId: null,
    refInvoiceNumber: null,
    reason: null,
    customerId: null,
    buyerName: "Sagarmatha Suppliers",
    buyerPan: "609876543",
    buyerAddress: "Lalitpur-10, Jhamsikhel",
    buyerPhone: "+977-9801234567",
    issuedAt,
    miti: toBsString(issuedAt),
    subTotalPaisa: totals.subTotalPaisa,
    discountPaisa: totals.discountPaisa,
    taxableAmountPaisa: totals.taxableAmountPaisa,
    nonTaxableAmountPaisa: totals.nonTaxableAmountPaisa,
    vatRateBp: 1300,
    vatAmountPaisa: totals.vatAmountPaisa,
    roundOffPaisa: 0,
    totalPaisa: totals.totalPaisa,
    amountInWords: amountInWords(totals.totalPaisa),
    paymentMethod: "cash" as const,
    notes: "Delivery within Kathmandu valley.",
    status: "active" as const,
    cancelledAt: null,
    cancelledBy: null,
    printCount: 0,
    firstPrintedAt: null,
    lastPrintedAt: null,
    enteredById: "u1",
    enteredByName: "Nischal Dahal",
    irdSyncStatus: "pending" as const,
    irdSyncedAt: null,
    irdSyncAttempts: 0,
    irdLastError: null,
    irdResponse: null,
    isRealtime: true,
    pdfKey: null,
    pdfSha256: null,
    pdfBytes: null,
    createdAt: issuedAt,
  };
  const items = totals.lines.map((line, index) => ({
    id: `l${index}`,
    invoiceId: "i1",
    lineNo: line.lineNo,
    itemId: null,
    description: line.description,
    hsCode: line.hsCode ?? null,
    unit: line.unit,
    quantityMilli: line.quantityMilli,
    unitPricePaisa: line.unitPricePaisa,
    discountPaisa: line.discountPaisa,
    vatApplicable: line.vatApplicable,
    lineTotalPaisa: line.lineTotalPaisa,
  }));

  return { store, invoice, items, totals };
}

describe("computeInvoice", () => {
  it("charges VAT on the discounted taxable value only", () => {
    const { totals } = fixture();

    // 3 x 450.00 = 1350.00, 1 x 12500.00 less 250.00 = 12250.00, 12 x 110.00 exempt = 1320.00
    expect(totals.subTotalPaisa).toBe(1_350_00 + 12_250_00 + 1_320_00);
    expect(totals.discountPaisa).toBe(5_000);
    expect(totals.taxableAmountPaisa + totals.nonTaxableAmountPaisa).toBe(
      totals.subTotalPaisa - totals.discountPaisa,
    );
    expect(totals.vatAmountPaisa).toBe(Math.round((totals.taxableAmountPaisa * 13) / 100));
    expect(totals.totalPaisa).toBe(
      totals.taxableAmountPaisa + totals.nonTaxableAmountPaisa + totals.vatAmountPaisa,
    );
  });

  it("never lets the split discount drift from the invoice discount", () => {
    const totals = computeInvoice({
      lines: [
        { ...lines[0], quantityMilli: 1000, unitPricePaisa: 3_333 },
        { ...lines[0], quantityMilli: 1000, unitPricePaisa: 3_333 },
        { ...lines[0], quantityMilli: 1000, unitPricePaisa: 3_334 },
      ],
      invoiceDiscountPaisa: 1_000,
    });

    expect(totals.taxableAmountPaisa).toBe(10_000 - 1_000);
  });
});

describe("buildInvoiceDocument", () => {
  it("prints every field Rule 17 requires on a tax invoice", () => {
    const { store, invoice, items } = fixture();
    const { html } = buildInvoiceDocument({ store, invoice, items, format: "a4", copyNumber: 1 });

    expect(html).toContain("TAX INVOICE");
    expect(html).toContain("कर बीजक");
    expect(html).toContain(store.name);
    expect(html).toContain(store.pan);
    expect(html).toContain(invoice.buyerName);
    expect(html).toContain(invoice.buyerPan);
    expect(html).toContain(invoice.invoiceNumber);
    expect(html).toContain(invoice.fiscalYear);
    expect(html).toContain("VAT @ 13%");
    expect(html).toContain(formatPaisa(invoice.totalPaisa));
    expect(html).toContain(invoice.amountInWords);
    expect(html).toContain("Original");
  });

  it("marks a reprint as a copy and a cancelled bill as cancelled", () => {
    const { store, invoice, items } = fixture();
    const reprint = buildInvoiceDocument({
      store,
      invoice: { ...invoice, status: "cancelled", printCount: 2 },
      items,
      format: "a4",
      copyNumber: 3,
    });

    expect(reprint.html).toContain("Copy of Original (#3)");
    expect(reprint.html).toContain("CANCELLED");
  });

  it("builds an 80mm receipt for the counter printer", () => {
    const { store, invoice, items } = fixture();
    const { html, printCss } = buildInvoiceDocument({
      store,
      invoice,
      items,
      format: "thermal80",
      copyNumber: 1,
    });

    expect(printCss).toContain("size: 80mm auto");
    expect(html).toContain("TOTAL");
    expect(html).toContain(store.pan);
  });
});

describe("renderInvoicePdf", () => {
  it("renders A4 and 80mm PDFs, including the Devanagari lines", { timeout: 120_000 }, async () => {
    const { store, invoice, items } = fixture();

    const a4 = await renderInvoicePdf({ store, invoice, items, format: "a4", copyNumber: 1 });
    expect(new TextDecoder().decode(a4.bytes.slice(0, 5))).toBe("%PDF-");
    expect(a4.bytes.byteLength).toBeGreaterThan(1000);
    expect(a4.sha256).toMatch(/^[0-9a-f]{64}$/);

    const receipt = await renderInvoicePdf({
      store,
      invoice,
      items,
      format: "thermal80",
      copyNumber: 1,
    });
    expect(new TextDecoder().decode(receipt.bytes.slice(0, 5))).toBe("%PDF-");

    // Same input, same bytes: the archived copy is reproducible.
    const again = await renderInvoicePdf({ store, invoice, items, format: "a4", copyNumber: 1 });
    expect(again.sha256).toBe(a4.sha256);

    if (process.env.DUMP_PDF) {
      const { writeFile } = await import("node:fs/promises");
      await writeFile(`${process.env.DUMP_PDF}/invoice-a4.pdf`, a4.bytes);
      await writeFile(`${process.env.DUMP_PDF}/invoice-thermal.pdf`, receipt.bytes);
    }
  });
});
