import { queryOptions } from "@tanstack/react-query";

import { $getMyStore, $listCustomers, $listItems } from "./functions";

export const storeQueryOptions = () =>
  queryOptions({
    queryKey: ["store"],
    queryFn: ({ signal }) => $getMyStore({ signal }),
  });

export const customersQueryOptions = (search?: string) =>
  queryOptions({
    queryKey: ["customers", search ?? ""],
    queryFn: ({ signal }) => $listCustomers({ data: search ? { search } : undefined, signal }),
  });

export const itemsQueryOptions = (search?: string, includeInactive = false) =>
  queryOptions({
    queryKey: ["items", search ?? "", includeInactive],
    queryFn: ({ signal }) =>
      $listItems({ data: { search: search || undefined, includeInactive }, signal }),
  });
