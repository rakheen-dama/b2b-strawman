"use client";

import { useRouter } from "next/navigation";
import { useCallback, useRef } from "react";
import { Input } from "@/components/ui/input";

interface TransactionFiltersProps {
  slug: string;
  search: {
    status?: string;
    type?: string;
    dateFrom?: string;
    dateTo?: string;
    customerId?: string;
    projectId?: string;
    page?: string;
  };
}

export function TransactionFilters({ slug, search }: TransactionFiltersProps) {
  const router = useRouter();
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(null);

  const navigate = useCallback(
    (overrides: Record<string, string | undefined>) => {
      const merged = { ...search, ...overrides };
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(merged)) {
        if (value && key !== "page") params.set(key, value);
      }
      const qs = params.toString();
      router.push(
        `/org/${slug}/trust-accounting/transactions${qs ? `?${qs}` : ""}`,
      );
    },
    [router, slug, search],
  );

  function handleDateChange(field: "dateFrom" | "dateTo", value: string) {
    navigate({ [field]: value || undefined });
  }

  function handleTextChange(field: "customerId" | "projectId", value: string) {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      navigate({ [field]: value || undefined });
    }, 500);
  }

  return (
    <div
      className="flex flex-wrap items-end gap-4"
      data-testid="advanced-filters"
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

      <div className="flex flex-col gap-1">
        <label
          htmlFor="filter-customerId"
          className="text-sm font-medium text-slate-600 dark:text-slate-400"
        >
          Client ID
        </label>
        <Input
          id="filter-customerId"
          type="text"
          placeholder="Client UUID"
          defaultValue={search.customerId ?? ""}
          onChange={(e) => handleTextChange("customerId", e.target.value)}
          className="w-52"
        />
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="filter-projectId"
          className="text-sm font-medium text-slate-600 dark:text-slate-400"
        >
          Matter ID
        </label>
        <Input
          id="filter-projectId"
          type="text"
          placeholder="Matter UUID"
          defaultValue={search.projectId ?? ""}
          onChange={(e) => handleTextChange("projectId", e.target.value)}
          className="w-52"
        />
      </div>
    </div>
  );
}
