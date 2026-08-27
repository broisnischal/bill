import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { InfoIcon, LoaderCircleIcon, PlusIcon, Trash2Icon } from "lucide-react";
import { useMemo, useState } from "react";

import { Money } from "#/components/money.tsx";
import { Button } from "#/components/ui/button.tsx";
import { Card, CardContent, CardHeader, CardTitle } from "#/components/ui/card.tsx";
import { Checkbox } from "#/components/ui/checkbox.tsx";
import { Input } from "#/components/ui/input.tsx";
import { Label } from "#/components/ui/label.tsx";
import { NativeSelect } from "#/components/ui/native-select.tsx";
import { Textarea } from "#/components/ui/textarea.tsx";
import { toast } from "#/components/ui/toast.tsx";
import { ABBREVIATED_INVOICE_LIMIT_PAISA, computeInvoice, roundPaisa } from "#/lib/invoice/calc.ts";
import { $createInvoice } from "#/lib/invoice/functions.ts";
import { paymentMethods } from "#/lib/invoice/schema.ts";
import { amountInWords, parsePaisa, parseQuantityMilli } from "#/lib/nepali/money.ts";
import {
  customersQueryOptions,
  itemsQueryOptions,
  storeQueryOptions,
} from "#/lib/store/queries.ts";

export const Route = createFileRoute("/_auth/app/invoices/new")({
  component: NewInvoicePage,
});

interface LineDraft {
  key: string;
  itemId?: string;
  description: string;
  hsCode: string;
  unit: string;
  quantity: string;
  rate: string;
  discount: string;
  vatApplicable: boolean;
}

const emptyLine = (): LineDraft => ({
  key: crypto.randomUUID(),
  description: "",
  hsCode: "",
  unit: "pcs",
  quantity: "1",
  rate: "",
  discount: "",
  vatApplicable: true,
});

/** Parses a draft line, returning null while it is still incomplete. */
function parseLine(line: LineDraft) {
  if (!line.description.trim() || !line.rate.trim()) return null;
  try {
    const quantityMilli = parseQuantityMilli(line.quantity || "0");
    if (quantityMilli <= 0) return null;
    return {
      itemId: line.itemId,
      description: line.description.trim(),
      hsCode: line.hsCode.trim() || undefined,
      unit: line.unit.trim() || "pcs",
      quantityMilli,
      unitPricePaisa: parsePaisa(line.rate),
      discountPaisa: line.discount ? parsePaisa(line.discount) : 0,
      vatApplicable: line.vatApplicable,
    };
  } catch {
    return null;
  }
}

function NewInvoicePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: membership } = useQuery(storeQueryOptions());
  const itemsQuery = useQuery(itemsQueryOptions());
  const customersQuery = useQuery(customersQueryOptions());

  const [lines, setLines] = useState<LineDraft[]>([emptyLine()]);
  const [invoiceType, setInvoiceType] = useState<"tax_invoice" | "abbreviated_tax_invoice">(
    "tax_invoice",
  );
  const [buyer, setBuyer] = useState({ id: "", name: "", pan: "", address: "", phone: "" });
  const [saveCustomer, setSaveCustomer] = useState(false);
  const [paymentMethod, setPaymentMethod] =
    useState<(typeof paymentMethods)[number]["value"]>("cash");
  const [invoiceDiscount, setInvoiceDiscount] = useState("");
  const [notes, setNotes] = useState("");

  const vatRateBp = membership?.store.taxpayerType === "vat" ? membership.store.vatRateBp : 0;

  const totals = useMemo(() => {
    const parsed = lines.map(parseLine).filter((line): line is NonNullable<typeof line> => !!line);
    let discountPaisa = 0;
    try {
      discountPaisa = invoiceDiscount ? parsePaisa(invoiceDiscount) : 0;
    } catch {
      discountPaisa = 0;
    }
    if (!parsed.length) return null;
    return computeInvoice({ lines: parsed, invoiceDiscountPaisa: discountPaisa, vatRateBp });
  }, [lines, invoiceDiscount, vatRateBp]);

  const overAbbreviatedLimit =
    invoiceType === "abbreviated_tax_invoice" &&
    (totals?.totalPaisa ?? 0) > ABBREVIATED_INVOICE_LIMIT_PAISA;
  const panRecommended =
    invoiceType === "tax_invoice" && (totals?.totalPaisa ?? 0) >= 10_000 * 100 && !buyer.pan;

  const { mutate, isPending } = useMutation({
    mutationFn: $createInvoice,
    onSuccess: (invoice) => {
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
      queryClient.invalidateQueries({ queryKey: ["sales-analytics"] });
      queryClient.invalidateQueries({ queryKey: ["ird-sync-status"] });
      toast.add({ description: `Bill ${invoice.invoiceNumber} issued.` });
      navigate({
        to: "/app/invoices/$invoiceId",
        params: { invoiceId: invoice.id },
        search: { print: "thermal80" },
      });
    },
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  const updateLine = (key: string, patch: Partial<LineDraft>) =>
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...patch } : line)));

  const applyCatalogItem = (key: string, itemId: string) => {
    const item = itemsQuery.data?.find((candidate) => candidate.id === itemId);
    if (!item) {
      updateLine(key, { itemId: undefined });
      return;
    }
    updateLine(key, {
      itemId: item.id,
      description: item.name,
      hsCode: item.hsCode ?? "",
      unit: item.unit,
      rate: (item.unitPricePaisa / 100).toFixed(2),
      vatApplicable: item.vatApplicable,
    });
  };

  const applyCustomer = (customerId: string) => {
    const customer = customersQuery.data?.find((candidate) => candidate.id === customerId);
    if (!customer) {
      setBuyer({ id: "", name: "", pan: "", address: "", phone: "" });
      return;
    }
    setBuyer({
      id: customer.id,
      name: customer.name,
      pan: customer.pan ?? "",
      address: customer.address ?? "",
      phone: customer.phone ?? "",
    });
  };

  const handleSubmit = (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isPending) return;

    const parsed = lines.map(parseLine).filter((line): line is NonNullable<typeof line> => !!line);
    if (!parsed.length) {
      toast.add({ type: "error", description: "Add at least one complete line." });
      return;
    }
    if (!buyer.name.trim()) {
      toast.add({ type: "error", description: "Enter the buyer's name." });
      return;
    }
    if (overAbbreviatedLimit) {
      toast.add({
        type: "error",
        description: "An abbreviated tax invoice cannot exceed NPR 10,000.",
      });
      return;
    }

    mutate({
      data: {
        invoiceType,
        customerId: buyer.id || undefined,
        buyerName: buyer.name.trim(),
        buyerPan: buyer.pan.trim() || undefined,
        buyerAddress: buyer.address.trim() || undefined,
        buyerPhone: buyer.phone.trim() || undefined,
        paymentMethod,
        notes: notes.trim() || undefined,
        discountPaisa: invoiceDiscount ? parsePaisa(invoiceDiscount) : 0,
        saveCustomer: saveCustomer && !buyer.id,
        lines: parsed,
      },
    });
  };

  return (
    <form
      onSubmit={handleSubmit}
      aria-busy={isPending}
      className="grid gap-6 lg:grid-cols-[1fr_360px]"
    >
      <div className="grid gap-6">
        <header className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">New bill</h1>
            <p className="text-sm text-muted-foreground">
              The number is issued when you save, in sequence and with no gaps.
            </p>
          </div>
          <div className="w-56">
            <Label htmlFor="invoiceType" className="mb-2 text-xs text-muted-foreground">
              Document type
            </Label>
            <NativeSelect
              id="invoiceType"
              value={invoiceType}
              onChange={(event) =>
                setInvoiceType(
                  event.target.value === "abbreviated_tax_invoice"
                    ? "abbreviated_tax_invoice"
                    : "tax_invoice",
                )
              }
            >
              <option value="tax_invoice">Tax invoice (कर बीजक)</option>
              <option value="abbreviated_tax_invoice">Abbreviated (up to Rs. 10,000)</option>
            </NativeSelect>
          </div>
        </header>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Lines</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4">
            {lines.map((line, index) => {
              const parsed = parseLine(line);
              return (
                <div key={line.key} className="grid gap-3 rounded-2xl border p-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-medium text-muted-foreground">
                      Line {index + 1}
                    </span>
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-medium">
                        {parsed ? (
                          <Money
                            paisa={Math.max(
                              0,
                              roundPaisa((parsed.quantityMilli * parsed.unitPricePaisa) / 1000) -
                                parsed.discountPaisa,
                            )}
                          />
                        ) : (
                          "-"
                        )}
                      </span>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Remove line ${index + 1}`}
                        disabled={lines.length === 1}
                        onClick={() =>
                          setLines((current) => current.filter((entry) => entry.key !== line.key))
                        }
                      >
                        <Trash2Icon aria-hidden="true" />
                      </Button>
                    </div>
                  </div>

                  <div className="grid gap-3 sm:grid-cols-[1fr_1fr]">
                    <div className="grid gap-2">
                      <Label
                        htmlFor={`catalog-${line.key}`}
                        className="text-xs text-muted-foreground"
                      >
                        From catalogue
                      </Label>
                      <NativeSelect
                        id={`catalog-${line.key}`}
                        value={line.itemId ?? ""}
                        onChange={(event) => applyCatalogItem(line.key, event.target.value)}
                      >
                        <option value="">Type it manually</option>
                        {itemsQuery.data?.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name}
                          </option>
                        ))}
                      </NativeSelect>
                    </div>
                    <div className="grid gap-2">
                      <Label htmlFor={`desc-${line.key}`} className="text-xs text-muted-foreground">
                        Particulars
                      </Label>
                      <Input
                        id={`desc-${line.key}`}
                        value={line.description}
                        onChange={(event) =>
                          updateLine(line.key, { description: event.target.value })
                        }
                        placeholder="Item or service"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
                    <NumberField
                      id={`qty-${line.key}`}
                      label="Qty"
                      value={line.quantity}
                      onChange={(value) => updateLine(line.key, { quantity: value })}
                    />
                    <div className="grid gap-2">
                      <Label htmlFor={`unit-${line.key}`} className="text-xs text-muted-foreground">
                        Unit
                      </Label>
                      <Input
                        id={`unit-${line.key}`}
                        value={line.unit}
                        onChange={(event) => updateLine(line.key, { unit: event.target.value })}
                      />
                    </div>
                    <NumberField
                      id={`rate-${line.key}`}
                      label="Rate"
                      value={line.rate}
                      onChange={(value) => updateLine(line.key, { rate: value })}
                    />
                    <NumberField
                      id={`disc-${line.key}`}
                      label="Discount"
                      value={line.discount}
                      onChange={(value) => updateLine(line.key, { discount: value })}
                    />
                    <div className="grid gap-2">
                      <Label htmlFor={`hs-${line.key}`} className="text-xs text-muted-foreground">
                        HS code
                      </Label>
                      <Input
                        id={`hs-${line.key}`}
                        value={line.hsCode}
                        onChange={(event) => updateLine(line.key, { hsCode: event.target.value })}
                      />
                    </div>
                  </div>

                  {vatRateBp > 0 && (
                    <Label className="flex w-fit items-center gap-2 text-xs text-muted-foreground">
                      <Checkbox
                        checked={line.vatApplicable}
                        onCheckedChange={(checked) =>
                          updateLine(line.key, { vatApplicable: checked === true })
                        }
                      />
                      VAT applies to this line
                    </Label>
                  )}
                </div>
              );
            })}

            <Button
              type="button"
              variant="outline"
              onClick={() => setLines((current) => [...current, emptyLine()])}
            >
              <PlusIcon aria-hidden="true" />
              Add line
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Buyer</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="grid gap-2 sm:col-span-2">
              <Label htmlFor="customer" className="text-xs text-muted-foreground">
                Existing customer
              </Label>
              <NativeSelect
                id="customer"
                value={buyer.id}
                onChange={(event) => applyCustomer(event.target.value)}
              >
                <option value="">Walk-in / new buyer</option>
                {customersQuery.data?.map((customer) => (
                  <option key={customer.id} value={customer.id}>
                    {customer.name}
                    {customer.pan ? ` · ${customer.pan}` : ""}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="buyerName">Name</Label>
              <Input
                id="buyerName"
                value={buyer.name}
                onChange={(event) => setBuyer({ ...buyer, name: event.target.value })}
                placeholder="Buyer name"
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="buyerPan">Buyer PAN</Label>
              <Input
                id="buyerPan"
                value={buyer.pan}
                onChange={(event) => setBuyer({ ...buyer, pan: event.target.value })}
                inputMode="numeric"
                maxLength={9}
                placeholder="9 digits"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="buyerAddress">Address</Label>
              <Input
                id="buyerAddress"
                value={buyer.address}
                onChange={(event) => setBuyer({ ...buyer, address: event.target.value })}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="buyerPhone">Phone</Label>
              <Input
                id="buyerPhone"
                value={buyer.phone}
                onChange={(event) => setBuyer({ ...buyer, phone: event.target.value })}
              />
            </div>
            {!buyer.id && (
              <Label className="flex w-fit items-center gap-2 text-xs text-muted-foreground sm:col-span-2">
                <Checkbox
                  checked={saveCustomer}
                  onCheckedChange={(checked) => setSaveCustomer(checked === true)}
                />
                Save this buyer to the customer list
              </Label>
            )}
          </CardContent>
        </Card>
      </div>

      <aside className="grid h-fit gap-4 lg:sticky lg:top-8">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Totals</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3 text-sm">
            <Row label="Sub total" value={<Money paisa={totals?.subTotalPaisa ?? 0} />} />
            <div className="grid gap-2">
              <Label htmlFor="invoiceDiscount" className="text-xs text-muted-foreground">
                Invoice discount
              </Label>
              <Input
                id="invoiceDiscount"
                value={invoiceDiscount}
                onChange={(event) => setInvoiceDiscount(event.target.value)}
                inputMode="decimal"
                placeholder="0.00"
              />
            </div>
            {(totals?.nonTaxableAmountPaisa ?? 0) > 0 && (
              <Row label="Exempt" value={<Money paisa={totals?.nonTaxableAmountPaisa ?? 0} />} />
            )}
            <Row label="Taxable" value={<Money paisa={totals?.taxableAmountPaisa ?? 0} />} />
            <Row
              label={`VAT @ ${(vatRateBp / 100).toFixed(0)}%`}
              value={<Money paisa={totals?.vatAmountPaisa ?? 0} />}
            />
            <div className="flex items-baseline justify-between border-t pt-3 text-lg font-semibold">
              <span>Total</span>
              <Money paisa={totals?.totalPaisa ?? 0} prefix />
            </div>
            {totals && (
              <p className="text-xs text-muted-foreground">{amountInWords(totals.totalPaisa)}</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Payment</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="grid gap-2">
              <Label htmlFor="paymentMethod" className="text-xs text-muted-foreground">
                Method
              </Label>
              <NativeSelect
                id="paymentMethod"
                value={paymentMethod}
                onChange={(event) => setPaymentMethod(event.target.value as typeof paymentMethod)}
              >
                {paymentMethods.map((method) => (
                  <option key={method.value} value={method.value}>
                    {method.label}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="notes" className="text-xs text-muted-foreground">
                Note on the bill
              </Label>
              <Textarea
                id="notes"
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
                rows={2}
              />
            </div>
          </CardContent>
        </Card>

        {(overAbbreviatedLimit || panRecommended) && (
          <div className="flex gap-2 rounded-2xl border border-dashed p-3 text-xs text-muted-foreground">
            <InfoIcon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>
              {overAbbreviatedLimit
                ? "Above Rs. 10,000 this has to be a full tax invoice."
                : "Bills of Rs. 10,000 or more: record the buyer's PAN if they want to claim the input credit."}
            </span>
          </div>
        )}

        <Button type="submit" size="lg" disabled={isPending || !totals}>
          {isPending && <LoaderCircleIcon className="animate-spin" aria-hidden="true" />}
          {isPending ? "Issuing..." : "Issue bill and print"}
        </Button>
      </aside>
    </form>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between">
      <span className="text-muted-foreground">{label}</span>
      {value}
    </div>
  );
}

function NumberField({
  id,
  label,
  value,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="grid gap-2">
      <Label htmlFor={id} className="text-xs text-muted-foreground">
        {label}
      </Label>
      <Input
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        inputMode="decimal"
        className="text-right font-mono"
      />
    </div>
  );
}
