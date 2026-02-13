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
    <div className="flex gap-1 rounded-lg border border-olive-200 p-0.5 dark:border-olive-800">
      {FILTERS.map((filter) => (
        <button
          key={filter.label}
          type="button"
          onClick={() => onFilterChange(filter.value)}
          className={cn(
            "rounded-md px-3 py-1 text-sm font-medium transition-colors",
            currentFilter === filter.value
              ? "bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-200"
              : "text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-100"
          )}
        >
          {filter.label}
        </button>
      ))}
    </div>
  );
}
