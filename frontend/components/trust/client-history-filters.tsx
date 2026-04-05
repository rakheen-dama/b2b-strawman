"use client";

import { useRouter } from "next/navigation";
import { useCallback } from "react";
import { Input } from "@/components/ui/input";

interface ClientHistoryFiltersProps {
  slug: string;
  customerId: string;
  search: {
    status?: string;
    type?: string;
    dateFrom?: string;
    dateTo?: string;
    page?: string;
  };
}

export function ClientHistoryFilters({
  slug,
  customerId,
  search,
}: ClientHistoryFiltersProps) {
  const router = useRouter();

  const navigate = useCallback(
    (overrides: Record<string, string | undefined>) => {
      const merged = { ...search, ...overrides };
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(merged)) {
        if (value && key !== "page") params.set(key, value);
      }
      const qs = params.toString();
      router.push(
        `/org/${slug}/trust-accounting/client-ledgers/${customerId}${qs ? `?${qs}` : ""}`,
      );
    },
    [router, slug, customerId, search],
  );

  function handleDateChange(field: "dateFrom" | "dateTo", value: string) {
    navigate({ [field]: value || undefined });
  }

  return (
    <div
      className="flex flex-wrap items-end gap-4"
      data-testid="date-filters"
    >
      <div className="flex flex-col gap-1">
        <label
          htmlFor="filter-dateFrom"
          className="text-sm font-medium text-slate-600 dark:text-slate-400"
        >
          From
        </label>
        <Input
          id="filter-dateFrom"
          type="date"
          defaultValue={search.dateFrom ?? ""}
          onChange={(e) => handleDateChange("dateFrom", e.target.value)}
          className="w-40"
        />
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="filter-dateTo"
          className="text-sm font-medium text-slate-600 dark:text-slate-400"
        >
          To
        </label>
        <Input
          id="filter-dateTo"
          type="date"
          defaultValue={search.dateTo ?? ""}
          onChange={(e) => handleDateChange("dateTo", e.target.value)}
          className="w-40"
        />
      </div>
    </div>
  );
}
