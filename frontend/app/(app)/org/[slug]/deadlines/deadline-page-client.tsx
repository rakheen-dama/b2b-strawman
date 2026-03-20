"use client";

import { useState, useTransition, useCallback } from "react";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { DeadlineFilters } from "@/components/deadlines/DeadlineFilters";
import { DeadlineListView } from "@/components/deadlines/DeadlineListView";
import { DeadlineCalendarView } from "@/components/deadlines/DeadlineCalendarView";
import { fetchDeadlines } from "./actions";
import type { CalculatedDeadline, DeadlineFiltersType } from "@/lib/types";

interface DeadlinePageClientProps {
  initialDeadlines: CalculatedDeadline[];
  initialTotal: number;
  initialYear: number;
  initialMonth: number;
  slug: string;
}

export function DeadlinePageClient({
  initialDeadlines,
  initialTotal,
  initialYear,
  initialMonth,
  slug,
}: DeadlinePageClientProps) {
  const [deadlines, setDeadlines] = useState(initialDeadlines);
  const [total, setTotal] = useState(initialTotal);
  const [year, setYear] = useState(initialYear);
  const [month, setMonth] = useState(initialMonth);
  const [view, setView] = useState<"list" | "calendar">("list");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [isPending, startTransition] = useTransition();

  const handleFilterChange = useCallback(
    (
      filters: Partial<DeadlineFiltersType>,
      newYear: number,
      newMonth: number
    ) => {
      setYear(newYear);
      setMonth(newMonth);
      setSelectedIds(new Set());
      startTransition(async () => {
        try {
          const result = await fetchDeadlines(
            filters.from ??
              `${newYear}-${String(newMonth).padStart(2, "0")}-01`,
            filters.to ??
              `${newYear}-${String(newMonth).padStart(2, "0")}-${new Date(newYear, newMonth, 0).getDate()}`,
            filters
          );
          setDeadlines(result.deadlines);
          setTotal(result.total);
        } catch (err) {
          console.error("Failed to refetch deadlines:", err);
        }
      });
    },
    []
  );

  const overdueCount = deadlines.filter((d) => d.status === "overdue").length;

  return (
    <div className="space-y-4">
      {/* Filters */}
      <DeadlineFilters
        initialYear={year}
        initialMonth={month}
        onFilterChange={handleFilterChange}
        isPending={isPending}
      />

      {/* View toggle + stats */}
      <div className="flex items-center justify-between">
        <Tabs
          value={view}
          onValueChange={(v) => setView(v as "list" | "calendar")}
        >
          <TabsList>
            <TabsTrigger value="list">List</TabsTrigger>
            <TabsTrigger value="calendar">Calendar</TabsTrigger>
          </TabsList>
        </Tabs>
        <div className="flex items-center gap-2">
          <span className="text-sm text-slate-600 dark:text-slate-400">
            {total} deadlines
          </span>
          {overdueCount > 0 && (
            <Badge variant="destructive">{overdueCount} overdue</Badge>
          )}
        </div>
      </div>

      {/* Content */}
      <div className={isPending ? "opacity-50 transition-opacity" : ""}>
        {view === "list" ? (
          <DeadlineListView
            deadlines={deadlines}
            slug={slug}
            selectedIds={selectedIds}
            onSelectionChange={setSelectedIds}
          />
        ) : (
          <DeadlineCalendarView
            deadlines={deadlines}
            year={year}
            month={month}
          />
        )}
      </div>
    </div>
  );
}
