import { ChevronDownIcon } from "lucide-react";

import { cn } from "#/lib/utils.ts";

/**
 * A plain `<select>`, styled like the rest of the inputs. Billing is keyboard work, and
 * a native dropdown is the fastest thing on a counter machine and on a phone.
 */
export function NativeSelect({ className, ...props }: React.ComponentPropsWithoutRef<"select">) {
  return (
    <div className="relative">
      <select
        data-slot="native-select"
        className={cn(
          "h-9 w-full appearance-none rounded-2xl border border-transparent bg-input/50 px-3 pr-9 text-sm transition-[color,box-shadow] outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20",
          className,
        )}
        {...props}
      />
      <ChevronDownIcon
        className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground"
        aria-hidden="true"
      />
    </div>
  );
}
