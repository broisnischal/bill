import { queryOptions } from "@tanstack/react-query";

import { $salesAnalytics } from "./functions";

export const salesAnalyticsQueryOptions = (fiscalYear: string) =>
  queryOptions({
    queryKey: ["sales-analytics", fiscalYear],
    queryFn: ({ signal }) => $salesAnalytics({ data: { fiscalYear }, signal }),
  });
