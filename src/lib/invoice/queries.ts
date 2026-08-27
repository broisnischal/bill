import { queryOptions } from "@tanstack/react-query";
import type * as z from "zod";

import { $getInvoice, $irdSyncStatus, $listInvoices, $salesRegister } from "./functions";
import type { invoiceListSchema } from "./schema";

type InvoiceListInput = z.input<typeof invoiceListSchema>;

export const invoicesQueryOptions = (input: InvoiceListInput) =>
  queryOptions({
    queryKey: ["invoices", input],
    queryFn: ({ signal }) => $listInvoices({ data: input, signal }),
  });

export const invoiceQueryOptions = (invoiceId: string) =>
  queryOptions({
    queryKey: ["invoice", invoiceId],
    queryFn: ({ signal }) => $getInvoice({ data: { invoiceId }, signal }),
  });

export const irdSyncStatusQueryOptions = () =>
  queryOptions({
    queryKey: ["ird-sync-status"],
    queryFn: ({ signal }) => $irdSyncStatus({ signal }),
  });

export const salesRegisterQueryOptions = (fiscalYear: string) =>
  queryOptions({
    queryKey: ["sales-register", fiscalYear],
    queryFn: ({ signal }) => $salesRegister({ data: { fiscalYear }, signal }),
  });
