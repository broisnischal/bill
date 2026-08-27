import { useMemo } from "react";

import { Input } from "#/components/ui/input.tsx";
import { bsStringToAd, toAdDateString, toBsString } from "#/lib/nepali/date.ts";

/**
 * Bikram Sambat date entry. Nepali paperwork is dated in BS, so that is what gets typed;
 * the Gregorian equivalent is shown underneath to catch a slip before it is saved.
 */
export function BsDateInput({
  id,
  name,
  value,
  onChange,
  required,
  disabled,
}: {
  id: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
}) {
  const preview = useMemo(() => {
    if (!/^2\d{3}-\d{2}-\d{2}$/.test(value)) return null;
    try {
      return toAdDateString(bsStringToAd(value));
    } catch {
      return null;
    }
  }, [value]);

  return (
    <div className="grid gap-1">
      <div className="flex gap-2">
        <Input
          id={id}
          name={name}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="2081-04-01"
          inputMode="numeric"
          autoComplete="off"
          required={required}
          disabled={disabled}
        />
        <button
          type="button"
          onClick={() => onChange(toBsString(new Date()))}
          className="shrink-0 rounded-2xl border px-3 text-xs text-muted-foreground hover:bg-accent"
          disabled={disabled}
        >
          Today
        </button>
      </div>
      <p className="text-xs text-muted-foreground">
        {preview ? `${preview} AD` : "BS date as YYYY-MM-DD"}
      </p>
    </div>
  );
}
