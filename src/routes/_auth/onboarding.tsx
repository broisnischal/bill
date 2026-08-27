import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, redirect, useNavigate } from "@tanstack/react-router";
import { LoaderCircleIcon, ShieldCheckIcon } from "lucide-react";
import { useState } from "react";
import * as z from "zod";

import { BsDateInput } from "#/components/bs-date-input.tsx";
import { Button } from "#/components/ui/button.tsx";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "#/components/ui/card.tsx";
import { Input } from "#/components/ui/input.tsx";
import { Label } from "#/components/ui/label.tsx";
import { NativeSelect } from "#/components/ui/native-select.tsx";
import { toast } from "#/components/ui/toast.tsx";
import { toBsString } from "#/lib/nepali/date.ts";
import { $createStore } from "#/lib/store/functions.ts";
import { storeQueryOptions } from "#/lib/store/queries.ts";
import { businessTypes, provinces, storeRegistrationSchema } from "#/lib/store/schema.ts";

export const Route = createFileRoute("/_auth/onboarding")({
  component: OnboardingPage,
  beforeLoad: async ({ context }) => {
    const membership = await context.queryClient.query({
      ...storeQueryOptions(),
      staleTime: "static",
    });
    if (membership) throw redirect({ to: "/app" });
  },
});

type FieldErrors = Partial<Record<string, string>>;

function OnboardingPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [registrationDateBs, setRegistrationDateBs] = useState(toBsString(new Date()));
  const [errors, setErrors] = useState<FieldErrors>({});

  const { mutate, isPending } = useMutation({
    mutationFn: (data: z.infer<typeof storeRegistrationSchema>) => $createStore({ data }),
    onSuccess: (membership) => {
      queryClient.setQueryData(storeQueryOptions().queryKey, membership);
      toast.add({ description: `${membership.store.name} is registered. Start billing.` });
      navigate({ to: "/app" });
    },
    onError: (error: Error) => {
      toast.add({ type: "error", description: error.message });
    },
  });

  const handleSubmit = (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isPending) return;

    const form = new FormData(event.currentTarget);
    const raw = Object.fromEntries(form.entries());
    const parsed = storeRegistrationSchema.safeParse({
      ...raw,
      ward: raw.ward === "" ? undefined : raw.ward,
      province: raw.province === "" ? undefined : raw.province,
    });

    if (!parsed.success) {
      const fieldErrors: FieldErrors = {};
      for (const issue of parsed.error.issues) {
        const key = String(issue.path[0]);
        fieldErrors[key] ??= issue.message;
      }
      setErrors(fieldErrors);
      toast.add({ type: "error", description: "Check the highlighted fields." });
      return;
    }

    setErrors({});
    mutate(parsed.data);
  };

  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-10">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">Register your business</CardTitle>
          <CardDescription>
            These details are printed on every bill you issue and reported to the IRD, so they have
            to match your PAN or VAT registration certificate exactly.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} aria-busy={isPending} className="grid gap-6">
            <section className="grid gap-4">
              <h2 className="text-sm font-semibold">Tax identity</h2>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Business name" name="name" error={errors.name} required>
                  <Input id="name" name="name" placeholder="Everest Traders Pvt. Ltd." required />
                </Field>
                <Field label="Name in Nepali" name="nameNepali" error={errors.nameNepali}>
                  <Input id="nameNepali" name="nameNepali" placeholder="सगरमाथा ट्रेडर्स" />
                </Field>
                <Field label="PAN / VAT number" name="pan" error={errors.pan} required>
                  <Input
                    id="pan"
                    name="pan"
                    placeholder="301234567"
                    inputMode="numeric"
                    maxLength={9}
                    required
                  />
                </Field>
                <Field label="Registration type" name="taxpayerType" error={errors.taxpayerType}>
                  <NativeSelect id="taxpayerType" name="taxpayerType" defaultValue="vat">
                    <option value="vat">VAT registered (charges 13% VAT)</option>
                    <option value="pan">PAN only (no VAT)</option>
                  </NativeSelect>
                </Field>
                <Field
                  label="Registration date (BS)"
                  name="registrationDateBs"
                  error={errors.registrationDateBs}
                  required
                >
                  <BsDateInput
                    id="registrationDateBs"
                    name="registrationDateBs"
                    value={registrationDateBs}
                    onChange={setRegistrationDateBs}
                    required
                  />
                </Field>
                <Field
                  label="Company registration no."
                  name="registrationNumber"
                  error={errors.registrationNumber}
                >
                  <Input
                    id="registrationNumber"
                    name="registrationNumber"
                    placeholder="129384/075/076"
                  />
                </Field>
                <Field label="Business type" name="businessType" error={errors.businessType}>
                  <NativeSelect
                    id="businessType"
                    name="businessType"
                    defaultValue="sole_proprietorship"
                  >
                    {businessTypes.map((type) => (
                      <option key={type.value} value={type.value}>
                        {type.label}
                      </option>
                    ))}
                  </NativeSelect>
                </Field>
                <Field label="Tax office (IRO)" name="taxOffice" error={errors.taxOffice}>
                  <Input id="taxOffice" name="taxOffice" placeholder="IRO Kathmandu 2" />
                </Field>
              </div>
            </section>

            <section className="grid gap-4">
              <h2 className="text-sm font-semibold">Address</h2>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Street / locality" name="address" error={errors.address} required>
                  <Input
                    id="address"
                    name="address"
                    placeholder="Naxal, Bhatbhateni Marg"
                    required
                  />
                </Field>
                <Field label="Ward" name="ward" error={errors.ward}>
                  <Input id="ward" name="ward" inputMode="numeric" placeholder="1" />
                </Field>
                <Field label="Municipality / VDC" name="municipality" error={errors.municipality}>
                  <Input
                    id="municipality"
                    name="municipality"
                    placeholder="Kathmandu Metropolitan City"
                  />
                </Field>
                <Field label="District" name="district" error={errors.district}>
                  <Input id="district" name="district" placeholder="Kathmandu" />
                </Field>
                <Field label="Province" name="province" error={errors.province}>
                  <NativeSelect id="province" name="province" defaultValue="">
                    <option value="">Select province</option>
                    {provinces.map((province) => (
                      <option key={province} value={province}>
                        {province}
                      </option>
                    ))}
                  </NativeSelect>
                </Field>
              </div>
            </section>

            <section className="grid gap-4">
              <h2 className="text-sm font-semibold">Contact</h2>
              <div className="grid gap-4 sm:grid-cols-3">
                <Field label="Phone" name="phone" error={errors.phone}>
                  <Input id="phone" name="phone" placeholder="+977-1-4412345" />
                </Field>
                <Field label="Email" name="email" error={errors.email}>
                  <Input
                    id="email"
                    name="email"
                    type="email"
                    placeholder="billing@example.com.np"
                  />
                </Field>
                <Field label="Website" name="website" error={errors.website}>
                  <Input id="website" name="website" placeholder="example.com.np" />
                </Field>
              </div>
            </section>

            <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
              <p className="flex items-center gap-2 text-xs text-muted-foreground">
                <ShieldCheckIcon className="size-4" aria-hidden="true" />
                The PAN cannot be changed later, because issued bills carry it.
              </p>
              <Button type="submit" size="lg" disabled={isPending}>
                {isPending && <LoaderCircleIcon className="animate-spin" aria-hidden="true" />}
                {isPending ? "Registering..." : "Register and start billing"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function Field({
  label,
  name,
  error,
  required,
  children,
}: {
  label: string;
  name: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="grid gap-2">
      <Label htmlFor={name}>
        {label}
        {required && <span className="text-destructive">*</span>}
      </Label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}
