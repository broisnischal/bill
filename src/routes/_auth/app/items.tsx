import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { LoaderCircleIcon, PlusIcon } from "lucide-react";
import { useState } from "react";

import { Money } from "#/components/money.tsx";
import { Button } from "#/components/ui/button.tsx";
import { Card, CardContent, CardHeader, CardTitle } from "#/components/ui/card.tsx";
import { Checkbox } from "#/components/ui/checkbox.tsx";
import { Input } from "#/components/ui/input.tsx";
import { Label } from "#/components/ui/label.tsx";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "#/components/ui/table.tsx";
import { toast } from "#/components/ui/toast.tsx";
import { parsePaisa } from "#/lib/nepali/money.ts";
import { $saveItem } from "#/lib/store/functions.ts";
import { itemsQueryOptions } from "#/lib/store/queries.ts";

/** FormData values can be files; billing forms only ever send text. */
const text = (value: FormDataEntryValue | null) => (typeof value === "string" ? value.trim() : "");

export const Route = createFileRoute("/_auth/app/items")({
  component: ItemsPage,
});

function ItemsPage() {
  const queryClient = useQueryClient();
  const items = useQuery(itemsQueryOptions(undefined, true));
  const [editing, setEditing] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: $saveItem,
    onSuccess: (item) => {
      queryClient.invalidateQueries({ queryKey: ["items"] });
      setEditing(null);
      toast.add({ description: `${item.name} saved.` });
    },
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  const handleSubmit = (event: React.SubmitEvent<HTMLFormElement>, id?: string) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const price = text(form.get("unitPrice")) || "0";

    let unitPricePaisa: number;
    try {
      unitPricePaisa = parsePaisa(price || "0");
    } catch {
      toast.add({ type: "error", description: "Enter a valid price." });
      return;
    }

    save.mutate({
      data: {
        id,
        name: text(form.get("name")),
        description: text(form.get("description")) || undefined,
        hsCode: text(form.get("hsCode")) || undefined,
        sku: text(form.get("sku")) || undefined,
        unit: text(form.get("unit")) || "pcs",
        unitPricePaisa,
        vatApplicable: form.get("vatApplicable") === "on",
        active: form.get("active") !== null ? form.get("active") === "on" : true,
      },
    });
  };

  return (
    <div className="grid gap-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">Items</h1>
        <p className="text-sm text-muted-foreground">
          What you sell, with its unit, price and whether VAT applies. Billing pulls from here.
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Add an item</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={(event) => handleSubmit(event)} className="grid gap-4 sm:grid-cols-6">
            <div className="grid gap-2 sm:col-span-2">
              <Label htmlFor="name">Name</Label>
              <Input id="name" name="name" required placeholder="Himalayan green tea 250g" />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="unit">Unit</Label>
              <Input id="unit" name="unit" defaultValue="pcs" />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="unitPrice">Price</Label>
              <Input
                id="unitPrice"
                name="unitPrice"
                inputMode="decimal"
                placeholder="450.00"
                className="text-right font-mono"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="hsCode">HS code</Label>
              <Input id="hsCode" name="hsCode" placeholder="0902.10" />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="sku">SKU</Label>
              <Input id="sku" name="sku" />
            </div>
            <div className="flex items-center justify-between gap-3 sm:col-span-6">
              <Label className="flex items-center gap-2 text-sm text-muted-foreground">
                <Checkbox name="vatApplicable" defaultChecked />
                VAT applies
              </Label>
              <Button type="submit" disabled={save.isPending}>
                {save.isPending && <LoaderCircleIcon className="animate-spin" aria-hidden="true" />}
                <PlusIcon aria-hidden="true" />
                Add item
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <div className="overflow-x-auto rounded-2xl border bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Unit</TableHead>
              <TableHead>HS code</TableHead>
              <TableHead className="text-right">Price</TableHead>
              <TableHead>VAT</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="text-muted-foreground">
                  No items yet.
                </TableCell>
              </TableRow>
            )}
            {items.data?.map((item) =>
              editing === item.id ? (
                <TableRow key={item.id}>
                  <TableCell colSpan={6}>
                    <form
                      onSubmit={(event) => handleSubmit(event, item.id)}
                      className="grid gap-3 sm:grid-cols-6"
                    >
                      <Input
                        name="name"
                        defaultValue={item.name}
                        className="sm:col-span-2"
                        required
                      />
                      <Input name="unit" defaultValue={item.unit} />
                      <Input
                        name="unitPrice"
                        defaultValue={(item.unitPricePaisa / 100).toFixed(2)}
                        inputMode="decimal"
                        className="text-right font-mono"
                      />
                      <Input name="hsCode" defaultValue={item.hsCode ?? ""} />
                      <Input name="sku" defaultValue={item.sku ?? ""} />
                      <div className="flex flex-wrap items-center gap-4 sm:col-span-6">
                        <Label className="flex items-center gap-2 text-sm text-muted-foreground">
                          <Checkbox name="vatApplicable" defaultChecked={item.vatApplicable} />
                          VAT applies
                        </Label>
                        <Label className="flex items-center gap-2 text-sm text-muted-foreground">
                          <Checkbox name="active" defaultChecked={item.active} />
                          Active
                        </Label>
                        <div className="ml-auto flex gap-2">
                          <Button type="button" variant="ghost" onClick={() => setEditing(null)}>
                            Cancel
                          </Button>
                          <Button type="submit" disabled={save.isPending}>
                            Save
                          </Button>
                        </div>
                      </div>
                    </form>
                  </TableCell>
                </TableRow>
              ) : (
                <TableRow key={item.id} className={item.active ? "" : "opacity-60"}>
                  <TableCell className="font-medium">{item.name}</TableCell>
                  <TableCell className="text-muted-foreground">{item.unit}</TableCell>
                  <TableCell className="text-muted-foreground">{item.hsCode ?? "-"}</TableCell>
                  <TableCell className="text-right">
                    <Money paisa={item.unitPricePaisa} />
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {item.vatApplicable ? "13%" : "Exempt"}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button variant="ghost" size="sm" onClick={() => setEditing(item.id)}>
                      Edit
                    </Button>
                  </TableCell>
                </TableRow>
              ),
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
