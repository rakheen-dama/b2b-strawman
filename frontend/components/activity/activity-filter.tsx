"use client";

import { cn } from "@/lib/utils";

interface ActivityFilterProps {
  onFilterChange: (entityType: string | null) => void;
  currentFilter: string | null;
}

const FILTERS = [
  { label: "All", value: null },
  { label: "Tasks", value: "TASK" },
  { label: "Documents", value: "DOCUMENT" },
  { label: "Comments", value: "COMMENT" },
  { label: "Members", value: "PROJECT_MEMBER" },
  { label: "Time", value: "TIME_ENTRY" },
] as const;

export function ActivityFilter({
  onFilterChange,
  currentFilter,
}: ActivityFilterProps) {
  return (
    <div className="flex gap-1 rounded-lg border border-slate-200 p-0.5 dark:border-slate-800">
      {FILTERS.map((filter) => (
        <button
          key={filter.label}
          type="button"
          onClick={() => onFilterChange(filter.value)}
          className={cn(
            "rounded-md px-3 py-1 text-sm font-medium transition-colors",
            currentFilter === filter.value
              ? "bg-teal-100 text-teal-700 dark:bg-teal-900 dark:text-teal-200"
              : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
          )}
        >
          {filter.label}
        </button>
      ))}
    </div>
  );
}
