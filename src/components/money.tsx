import { formatPaisa } from "#/lib/nepali/money.ts";
import { cn } from "#/lib/utils.ts";

/** Rupees, right-aligned and tabular, so columns of figures line up. */
export function Money({
  paisa,
  className,
  prefix,
}: {
  paisa: number;
  className?: string;
  prefix?: boolean;
}) {
  return (
    <span className={cn("font-mono tabular-nums", className)}>
      {prefix ? "Rs. " : ""}
      {formatPaisa(paisa)}
    </span>
  );
}
