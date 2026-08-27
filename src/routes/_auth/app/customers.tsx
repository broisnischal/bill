import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { LoaderCircleIcon, PlusIcon } from "lucide-react";
import { useState } from "react";

import { Button } from "#/components/ui/button.tsx";
import { Card, CardContent, CardHeader, CardTitle } from "#/components/ui/card.tsx";
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
import { $saveCustomer } from "#/lib/store/functions.ts";
import { customersQueryOptions } from "#/lib/store/queries.ts";

/** FormData values can be files; billing forms only ever send text. */
const text = (value: FormDataEntryValue | null) => (typeof value === "string" ? value.trim() : "");

export const Route = createFileRoute("/_auth/app/customers")({
  component: CustomersPage,
});

function CustomersPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const customers = useQuery(customersQueryOptions(search || undefined));

  const save = useMutation({
    mutationFn: $saveCustomer,
    onSuccess: (customer) => {
      queryClient.invalidateQueries({ queryKey: ["customers"] });
      toast.add({ description: `${customer.name} saved.` });
    },
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  const handleSubmit = (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    save.mutate({
      data: {
        name: text(form.get("name")),
        pan: text(form.get("pan")) || undefined,
        address: text(form.get("address")) || undefined,
        phone: text(form.get("phone")) || undefined,
        email: text(form.get("email")) || undefined,
      },
    });
    event.currentTarget.reset();
  };

  return (
    <div className="grid gap-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">Customers</h1>
        <p className="text-sm text-muted-foreground">
          A buyer's PAN belongs on the bill whenever they want to claim the input credit.
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Add a customer</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-5">
            <div className="grid gap-2 sm:col-span-2">
              <Label htmlFor="name">Name</Label>
              <Input id="name" name="name" required placeholder="Sagarmatha Suppliers" />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="pan">PAN</Label>
              <Input
                id="pan"
                name="pan"
                inputMode="numeric"
                maxLength={9}
                placeholder="609876543"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="phone">Phone</Label>
              <Input id="phone" name="phone" />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="address">Address</Label>
              <Input id="address" name="address" />
            </div>
            <div className="grid gap-2 sm:col-span-4">
              <Label htmlFor="email">Email</Label>
              <Input id="email" name="email" type="email" />
            </div>
            <div className="flex items-end">
              <Button type="submit" className="w-full" disabled={save.isPending}>
                {save.isPending && <LoaderCircleIcon className="animate-spin" aria-hidden="true" />}
                <PlusIcon aria-hidden="true" />
                Add
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Input
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        placeholder="Search by name or PAN"
        className="max-w-sm"
      />

      <div className="overflow-x-auto rounded-2xl border bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>PAN</TableHead>
              <TableHead>Phone</TableHead>
              <TableHead>Address</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {customers.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className="text-muted-foreground">
                  No customers yet.
                </TableCell>
              </TableRow>
            )}
            {customers.data?.map((customer) => (
              <TableRow key={customer.id}>
                <TableCell className="font-medium">{customer.name}</TableCell>
                <TableCell className="font-mono text-xs">{customer.pan ?? "-"}</TableCell>
                <TableCell className="text-muted-foreground">{customer.phone ?? "-"}</TableCell>
                <TableCell className="text-muted-foreground">{customer.address ?? "-"}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
