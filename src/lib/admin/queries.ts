import { queryOptions } from "@tanstack/react-query";

import { $isReviewer, $reviewQueue, $storeForReview } from "./functions";

export type ReviewStatusFilter = "pending" | "approved" | "rejected" | "all";

export const reviewQueueQueryOptions = (status: ReviewStatusFilter) =>
  queryOptions({
    queryKey: ["admin", "queue", status],
    queryFn: () => $reviewQueue({ data: { status } }),
  });

export const storeForReviewQueryOptions = (storeId: string) =>
  queryOptions({
    queryKey: ["admin", "store", storeId],
    queryFn: () => $storeForReview({ data: { storeId } }),
  });

export const isReviewerQueryOptions = () =>
  queryOptions({
    queryKey: ["admin", "is-reviewer"],
    queryFn: () => $isReviewer(),
    staleTime: 5 * 60 * 1000,
  });
