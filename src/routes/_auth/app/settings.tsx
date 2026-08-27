import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { LoaderCircleIcon, LockIcon } from "lucide-react";
import { useState } from "react";

import { BsDateInput } from "#/components/bs-date-input.tsx";
import { Button } from "#/components/ui/button.tsx";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "#/components/ui/card.tsx";
import { Checkbox } from "#/components/ui/checkbox.tsx";
import { Input } from "#/components/ui/input.tsx";
import { Label } from "#/components/ui/label.tsx";
import { NativeSelect } from "#/components/ui/native-select.tsx";
import { Textarea } from "#/components/ui/textarea.tsx";
import { toast } from "#/components/ui/toast.tsx";
import { $updateStore } from "#/lib/store/functions.ts";
import { storeQueryOptions } from "#/lib/store/queries.ts";
import { businessTypes, provinces, storeSettingsSchema } from "#/lib/store/schema.ts";

export const Route = createFileRoute("/_auth/app/settings")({
  component: SettingsPage,
});

function SettingsPage() {
  const queryClient = useQueryClient();
  const { data: membership } = useQuery(storeQueryOptions());
  const [registrationDateBs, setRegistrationDateBs] = useState(
    membership?.store.registrationDateBs ?? "",
  );
  const [cbmsEnabled, setCbmsEnabled] = useState(membership?.store.cbmsEnabled ?? false);

  const update = useMutation({
    mutationFn: $updateStore,
    onSuccess: (result) => {
      queryClient.setQueryData(storeQueryOptions().queryKey, result);
      toast.add({ description: "Settings saved." });
    },
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  if (!membership) return <p className="text-sm text-muted-foreground">Loading settings...</p>;
  const { store, role } = membership;

  const handleSubmit = (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const raw = Object.fromEntries(form.entries());

    const parsed = storeSettingsSchema.safeParse({
      ...raw,
      pan: store.pan,
      registrationDateBs: registrationDateBs || store.registrationDateBs,
      ward: raw.ward === "" ? undefined : raw.ward,
      province: raw.province === "" ? undefined : raw.province,
      cbmsEnabled: form.get("cbmsEnabled") === "on",
    });

    if (!parsed.success) {
      toast.add({ type: "error", description: parsed.error.issues[0].message });
      return;
    }

    update.mutate({ data: parsed.data });
  };

  const readOnly = role === "cashier";

  return (
    <form onSubmit={handleSubmit} aria-busy={update.isPending} className="grid max-w-4xl gap-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
        <p className="text-sm text-muted-foreground">
          What gets printed on the bill and how it reaches the IRD.
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Tax identity</CardTitle>
          <CardDescription>
            The PAN is fixed once bills have been issued under it. Everything else can change.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field label="PAN / VAT number">
            <div className="flex items-center gap-2 rounded-2xl bg-muted/60 px-3 py-2 font-mono text-sm">
              <LockIcon className="size-3.5 text-muted-foreground" aria-hidden="true" />
              {store.pan}
            </div>
          </Field>
          <Field label="Registration type" htmlFor="taxpayerType">
            <NativeSelect id="taxpayerType" name="taxpayerType" defaultValue={store.taxpayerType}>
              <option value="vat">VAT registered (13%)</option>
              <option value="pan">PAN only (no VAT)</option>
            </NativeSelect>
          </Field>
          <Field label="Business name" htmlFor="name">
            <Input id="name" name="name" defaultValue={store.name} required />
          </Field>
          <Field label="Name in Nepali" htmlFor="nameNepali">
            <Input id="nameNepali" name="nameNepali" defaultValue={store.nameNepali ?? ""} />
          </Field>
          <Field label="Registration date (BS)" htmlFor="registrationDateBs">
            <BsDateInput
              id="registrationDateBs"
              name="registrationDateBs"
              value={registrationDateBs || store.registrationDateBs}
              onChange={setRegistrationDateBs}
            />
          </Field>
          <Field label="Company registration no." htmlFor="registrationNumber">
            <Input
              id="registrationNumber"
              name="registrationNumber"
              defaultValue={store.registrationNumber ?? ""}
            />
          </Field>
          <Field label="Business type" htmlFor="businessType">
            <NativeSelect id="businessType" name="businessType" defaultValue={store.businessType}>
              {businessTypes.map((type) => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label="Tax office (IRO)" htmlFor="taxOffice">
            <Input id="taxOffice" name="taxOffice" defaultValue={store.taxOffice ?? ""} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Address and contact</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field label="Street / locality" htmlFor="address">
            <Input id="address" name="address" defaultValue={store.address} required />
          </Field>
          <Field label="Ward" htmlFor="ward">
            <Input id="ward" name="ward" inputMode="numeric" defaultValue={store.ward ?? ""} />
          </Field>
          <Field label="Municipality" htmlFor="municipality">
            <Input id="municipality" name="municipality" defaultValue={store.municipality ?? ""} />
          </Field>
          <Field label="District" htmlFor="district">
            <Input id="district" name="district" defaultValue={store.district ?? ""} />
          </Field>
          <Field label="Province" htmlFor="province">
            <NativeSelect id="province" name="province" defaultValue={store.province ?? ""}>
              <option value="">Select province</option>
              {provinces.map((province) => (
                <option key={province} value={province}>
                  {province}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label="Phone" htmlFor="phone">
            <Input id="phone" name="phone" defaultValue={store.phone ?? ""} />
          </Field>
          <Field label="Email" htmlFor="email">
            <Input id="email" name="email" type="email" defaultValue={store.email ?? ""} />
          </Field>
          <Field label="Website" htmlFor="website">
            <Input id="website" name="website" defaultValue={store.website ?? ""} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Bill layout</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field label="Invoice prefix" htmlFor="invoicePrefix">
            <Input
              id="invoicePrefix"
              name="invoicePrefix"
              defaultValue={store.invoicePrefix}
              placeholder="INV"
            />
            <p className="text-xs text-muted-foreground">
              Numbers read {store.invoicePrefix ? `${store.invoicePrefix}-` : ""}2082.083-000001.
              Changing this does not renumber issued bills.
            </p>
          </Field>
          <Field label="Footer note" htmlFor="printFooterNote">
            <Input
              id="printFooterNote"
              name="printFooterNote"
              defaultValue={store.printFooterNote ?? ""}
              placeholder="Goods once sold are not returnable."
            />
          </Field>
          <Field label="Bank details" htmlFor="bankDetails" className="sm:col-span-2">
            <Textarea
              id="bankDetails"
              name="bankDetails"
              rows={2}
              defaultValue={store.bankDetails ?? ""}
              placeholder="Nabil Bank · A/C 0123456789012 · Branch Naxal"
            />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">IRD Central Billing Monitoring System</CardTitle>
          <CardDescription>
            Turn this on once the IRD has approved this software against your PAN. Every bill and
            credit note is then pushed as it is issued, and anything that fails is queued and
            retried. The password is encrypted before it is stored.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Label className="flex items-center gap-2 text-sm sm:col-span-2">
            <Checkbox
              name="cbmsEnabled"
              checked={cbmsEnabled}
              onCheckedChange={(checked) => setCbmsEnabled(checked === true)}
            />
            Send bills to CBMS in real time
          </Label>
          <Field label="CBMS username" htmlFor="cbmsUsername">
            <Input id="cbmsUsername" name="cbmsUsername" defaultValue={store.cbmsUsername ?? ""} />
          </Field>
          <Field label="CBMS password" htmlFor="cbmsPassword">
            <Input
              id="cbmsPassword"
              name="cbmsPassword"
              type="password"
              placeholder={store.cbmsPasswordEncrypted ? "Stored. Leave blank to keep it." : ""}
              autoComplete="off"
            />
          </Field>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button type="submit" size="lg" disabled={update.isPending || readOnly}>
          {update.isPending && <LoaderCircleIcon className="animate-spin" aria-hidden="true" />}
          Save settings
        </Button>
      </div>
    </form>
  );
}

function Field({
  label,
  htmlFor,
  className,
  children,
}: {
  label: string;
  htmlFor?: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={`grid gap-2 ${className ?? ""}`}>
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  );
}
