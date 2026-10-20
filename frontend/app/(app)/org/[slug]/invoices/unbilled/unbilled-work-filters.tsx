"use client";

import { useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

interface UnbilledWorkFiltersProps {
  periodFrom: string;
  periodTo: string;
  currency: string;
  currencyOptions: string[];
}

/** Period + currency filter for the Unbilled work page — state lives in the URL search params. */
export function UnbilledWorkFilters({
  periodFrom,
  periodTo,
  currency,
  currencyOptions,
}: UnbilledWorkFiltersProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [from, setFrom] = useState(periodFrom);
  const [to, setTo] = useState(periodTo);
  const [curr, setCurr] = useState(currency);

  const rangeInverted = Boolean(from && to && from > to);
  const invalid = !from || !to || rangeInverted;

  function apply(e: React.FormEvent) {
    e.preventDefault();
    if (invalid) return;
    const qs = new URLSearchParams({ periodFrom: from, periodTo: to, currency: curr });
    router.replace(`${pathname}?${qs.toString()}`);
  }

  return (
    <form
      onSubmit={apply}
      className="flex flex-wrap items-end gap-4"
      aria-label="Unbilled work filters"
    >
      <div className="space-y-2">
        <Label htmlFor="unbilled-period-from">From</Label>
        <Input
          id="unbilled-period-from"
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="unbilled-period-to">To</Label>
        <Input
          id="unbilled-period-to"
          type="date"
          value={to}
          onChange={(e) => setTo(e.target.value)}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="unbilled-currency">Currency</Label>
        <Select value={curr} onValueChange={setCurr}>
          <SelectTrigger id="unbilled-currency" className="w-28" data-testid="unbilled-currency">
            <SelectValue placeholder="Currency" />
          </SelectTrigger>
          <SelectContent>
            {currencyOptions.map((c) => (
              <SelectItem key={c} value={c}>
                {c}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <Button type="submit" size="sm" disabled={invalid}>
        Apply
      </Button>
      {rangeInverted && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">
          The start date must be on or before the end date.
        </p>
      )}
    </form>
  );
}
