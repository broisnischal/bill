import * as z from "zod";

/**
 * Invoice arithmetic. Every amount is integer paisa and every quantity is thousandths
 * of a unit, so a bill totals the same on the screen, in the PDF and in the CBMS payload.
 */

/** Half-up rounding, the convention Nepali VAT returns are filed with. */
export function roundPaisa(value: number) {
  return value < 0 ? -Math.round(-value) : Math.round(value);
}

export const lineInputSchema = z.object({
  itemId: z.string().optional(),
  description: z.string().trim().min(1, "Description is required").max(500),
  hsCode: z.string().trim().max(20).optional(),
  unit: z.string().trim().min(1).max(20).default("pcs"),
  quantityMilli: z.int().positive("Quantity must be greater than zero"),
  unitPricePaisa: z.int().nonnegative(),
  discountPaisa: z.int().nonnegative().default(0),
  vatApplicable: z.boolean().default(true),
});

export type LineInput = z.infer<typeof lineInputSchema>;

export interface ComputedLine extends LineInput {
  lineNo: number;
  grossPaisa: number;
  lineTotalPaisa: number;
}

export interface InvoiceTotals {
  lines: ComputedLine[];
  subTotalPaisa: number;
  discountPaisa: number;
  taxableAmountPaisa: number;
  nonTaxableAmountPaisa: number;
  vatAmountPaisa: number;
  totalPaisa: number;
}

/**
 * Totals a bill.
 *
 * A line's gross is quantity times unit price; its own discount comes off first. An
 * invoice-level discount is then split across the lines in proportion to what is left,
 * so the taxable and exempt buckets each carry their fair share and VAT is charged on
 * the discounted taxable value, which is what Rule 17 asks for.
 */
export function computeInvoice({
  lines,
  invoiceDiscountPaisa = 0,
  vatRateBp = 1300,
}: {
  lines: LineInput[];
  invoiceDiscountPaisa?: number;
  vatRateBp?: number;
}): InvoiceTotals {
  const computed: ComputedLine[] = lines.map((line, index) => {
    const grossPaisa = roundPaisa((line.quantityMilli * line.unitPricePaisa) / 1000);
    return {
      ...line,
      lineNo: index + 1,
      grossPaisa,
      lineTotalPaisa: Math.max(0, grossPaisa - line.discountPaisa),
    };
  });

  const subTotalPaisa = computed.reduce((sum, line) => sum + line.lineTotalPaisa, 0);
  const discountPaisa = Math.min(Math.max(0, invoiceDiscountPaisa), subTotalPaisa);

  // Split the invoice discount across lines, giving the remainder to the last line so
  // the parts always add back up to the discount exactly.
  let allocated = 0;
  const shares = computed.map((line, index) => {
    if (discountPaisa === 0 || subTotalPaisa === 0) return 0;
    if (index === computed.length - 1) return discountPaisa - allocated;
    const share = Math.floor((line.lineTotalPaisa * discountPaisa) / subTotalPaisa);
    allocated += share;
    return share;
  });

  let taxableAmountPaisa = 0;
  let nonTaxableAmountPaisa = 0;
  computed.forEach((line, index) => {
    const net = line.lineTotalPaisa - shares[index];
    if (line.vatApplicable) taxableAmountPaisa += net;
    else nonTaxableAmountPaisa += net;
  });

  const vatAmountPaisa = roundPaisa((taxableAmountPaisa * vatRateBp) / 10_000);
  const totalPaisa = taxableAmountPaisa + nonTaxableAmountPaisa + vatAmountPaisa;

  return {
    lines: computed,
    subTotalPaisa,
    discountPaisa,
    taxableAmountPaisa,
    nonTaxableAmountPaisa,
    vatAmountPaisa,
    totalPaisa,
  };
}

/** Rule 18 caps an abbreviated tax invoice at NPR 10,000. */
export const ABBREVIATED_INVOICE_LIMIT_PAISA = 10_000 * 100;
