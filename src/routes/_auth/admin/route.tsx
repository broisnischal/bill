import { createFileRoute, Link, Outlet, redirect } from "@tanstack/react-router";
import { ShieldCheckIcon } from "lucide-react";

import { isReviewerQueryOptions } from "#/lib/admin/queries.ts";
import { authQueryOptions } from "#/lib/auth/queries.ts";

/**
 * The review desk.
 *
 * Deliberately outside the billing app: a reviewer is not a shopkeeper, and putting this
 * behind the same shell would give every shop a nav item leading to a 404. Access is the
 * `ADMIN_PHONES` allow-list, checked again on the server for every call the pages make —
 * this guard only decides what to render.
 */
export const Route = createFileRoute("/_auth/admin")({
  component: AdminLayout,
  beforeLoad: async ({ context }) => {
    const user = await context.queryClient.query({
      ...authQueryOptions(),
      staleTime: "static",
    });
    if (!user) throw redirect({ to: "/login" });

    // Not a reviewer is not an error. The server refuses every call this page would make
    // anyway; sending them back to billing beats a red box that reads like a crash.
    const reviewer = await context.queryClient.query({
      ...isReviewerQueryOptions(),
      staleTime: "static",
    });
    if (!reviewer) throw redirect({ to: "/app" });
  },
});

function AdminLayout() {
  return (
    <div className="min-h-svh bg-muted/30">
      <header className="border-b bg-background">
        <div className="mx-auto flex max-w-5xl items-center gap-3 px-6 py-4">
          <ShieldCheckIcon className="size-5" aria-hidden="true" />
          <Link to="/admin" className="font-semibold tracking-tight">
            Business review
          </Link>
          <Link
            to="/app"
            className="ml-auto text-sm text-muted-foreground underline-offset-4 hover:underline"
          >
            Back to billing
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}
