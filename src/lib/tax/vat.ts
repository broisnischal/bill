/**
 * VAT is off, everywhere, for now.
 *
 * Every shop this reaches first holds a PAN and is not registered for VAT. A bill that
 * charges 13% on their behalf is not a cosmetic mistake: it overcharges their customer
 * and claims a registration they do not have, and the shop is the one who answers for
 * it.
 *
 * The switch is here rather than per-store on purpose. A store row can still say
 * `taxpayerType = "vat"` — several do, from before this — and the flag makes that inert
 * until VAT is deliberately turned back on, at which point each store's own type decides
 * again who it applies to. Turning it on is this one line and a redeploy.
 */
export const VAT_ENABLED = false;

/** What a bill should charge, given the shop and the switch above. */
export function vatRateFor(store: { taxpayerType: string; vatRateBp: number }): number {
  return VAT_ENABLED && store.taxpayerType === "vat" ? store.vatRateBp : 0;
}

/** Whether this shop's paper may say "tax invoice". Only if it is actually charging tax. */
export function issuesTaxInvoice(store: { taxpayerType: string }): boolean {
  return VAT_ENABLED && store.taxpayerType === "vat";
}
