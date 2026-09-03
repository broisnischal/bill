import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link, Outlet, redirect } from "@tanstack/react-router";
import {
  BarChart3Icon,
  BuildingIcon,
  FileTextIcon,
  LayoutDashboardIcon,
  PackageIcon,
  PlusIcon,
  SettingsIcon,
  ShieldCheckIcon,
  UsersIcon,
} from "lucide-react";

import { SignOutButton } from "#/components/sign-out-button.tsx";
import { ThemeToggle } from "#/components/theme-toggle.tsx";
import { Button } from "#/components/ui/button.tsx";
import { isReviewerQueryOptions } from "#/lib/admin/queries.ts";
import { useAuth } from "#/lib/auth/hooks.ts";
import { storeQueryOptions } from "#/lib/store/queries.ts";

export const Route = createFileRoute("/_auth/app")({
  component: AppLayout,
  beforeLoad: async ({ context }) => {
    // A bill cannot exist without a registered taxpayer behind it, so the whole app
    // is gated on the business details being on file.
    const membership = await context.queryClient.query({
      ...storeQueryOptions(),
      staleTime: "static",
    });
    void context.queryClient.query(storeQueryOptions());

    if (!membership) throw redirect({ to: "/onboarding" });
  },
});

const NAV = [
  { to: "/app", label: "Dashboard", icon: LayoutDashboardIcon, exact: true },
  { to: "/app/invoices", label: "Invoices", icon: FileTextIcon, exact: false },
  { to: "/app/items", label: "Items", icon: PackageIcon, exact: false },
  { to: "/app/customers", label: "Customers", icon: UsersIcon, exact: false },
  { to: "/app/reports", label: "Reports", icon: BarChart3Icon, exact: false },
  { to: "/app/settings", label: "Settings", icon: SettingsIcon, exact: false },
] as const;

function AppLayout() {
  const { user } = useAuth();
  const { data: membership } = useQuery(storeQueryOptions());
  const { data: isReviewer } = useQuery(isReviewerQueryOptions());

  return (
    <div className="flex min-h-svh flex-col bg-muted/30 md:flex-row">
      <aside className="flex shrink-0 flex-col gap-4 border-b bg-background p-4 md:w-60 md:border-r md:border-b-0">
        <Link to="/app" className="flex flex-col gap-0.5">
          <span className="flex items-center gap-2 font-semibold tracking-tight">
            <BuildingIcon className="size-5" aria-hidden="true" />
            {membership?.store.name ?? "bill"}
          </span>
          {membership && (
            <span className="pl-7 font-mono text-xs text-muted-foreground">
              PAN {membership.store.pan}
            </span>
          )}
        </Link>

        <Button render={<Link to="/app/invoices/new" />} nativeButton={false} className="w-full">
          <PlusIcon aria-hidden="true" />
          New bill
        </Button>

        <nav className="-mx-1 flex gap-1 overflow-x-auto md:flex-col md:overflow-visible">
          {NAV.map((entry) => (
            <Link
              key={entry.to}
              to={entry.to}
              search={entry.to === "/app/invoices" ? { status: "all", page: 1 } : undefined}
              activeOptions={{ exact: entry.exact }}
              activeProps={{ className: "bg-accent text-accent-foreground" }}
              className="flex shrink-0 items-center gap-2 rounded-xl px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent/60 hover:text-foreground"
            >
              <entry.icon className="size-4" aria-hidden="true" />
              {entry.label}
            </Link>
          ))}

          {/* Only for the accounts on the reviewer list. Everyone else has no route to
              land on and would get a 404 for their trouble. */}
          {isReviewer && (
            <Link
              to="/admin"
              search={{ status: "pending" as const }}
              activeProps={{ className: "bg-accent text-accent-foreground" }}
              className="flex shrink-0 items-center gap-2 rounded-xl px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent/60 hover:text-foreground"
            >
              <ShieldCheckIcon className="size-4" aria-hidden="true" />
              Review
            </Link>
          )}
        </nav>

        <div className="mt-auto hidden flex-col gap-2 border-t pt-3 text-xs text-muted-foreground md:flex">
          <span className="truncate">{user?.name}</span>
          <div className="flex items-center justify-between">
            <SignOutButton />
            <ThemeToggle />
          </div>
        </div>
      </aside>

      <main className="min-w-0 flex-1 p-4 md:p-8">
        <Outlet />
      </main>
    </div>
  );
}
