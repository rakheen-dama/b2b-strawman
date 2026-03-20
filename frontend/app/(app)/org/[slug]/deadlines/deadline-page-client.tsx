"use client";

import { useState, useTransition, useCallback, useEffect } from "react";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { DeadlineFilters } from "@/components/deadlines/DeadlineFilters";
import { DeadlineListView } from "@/components/deadlines/DeadlineListView";
import { DeadlineCalendarView } from "@/components/deadlines/DeadlineCalendarView";
import { DeadlineSummaryCards } from "@/components/deadlines/DeadlineSummaryCards";
import { BatchFilingActions } from "@/components/deadlines/BatchFilingActions";
import { fetchDeadlines, fetchDeadlineSummary } from "./actions";
import type {
  CalculatedDeadline,
  DeadlineFiltersType,
  DeadlineSummary,
} from "@/lib/types";

type ViewMode = "list" | "calendar" | "summary";

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
  const [view, setView] = useState<ViewMode>("list");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [summaries, setSummaries] = useState<DeadlineSummary[]>([]);
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

  // Fetch summary data when view switches to "summary" or when month/year changes while in summary view
  useEffect(() => {
    if (view !== "summary") return;
    startTransition(async () => {
      try {
        const from = `${year}-${String(month).padStart(2, "0")}-01`;
        const to = `${year}-${String(month).padStart(2, "0")}-${new Date(year, month, 0).getDate()}`;
        const result = await fetchDeadlineSummary(from, to);
        setSummaries(result.summaries);
      } catch (err) {
        console.error("Failed to fetch deadline summary:", err);
      }
    });
  }, [view, year, month]);

  function handleViewChange(v: string) {
    setView(v as ViewMode);
  }

  function refetchDeadlines() {
    setSelectedIds(new Set());
    startTransition(async () => {
      try {
        const from = `${year}-${String(month).padStart(2, "0")}-01`;
        const to = `${year}-${String(month).padStart(2, "0")}-${new Date(year, month, 0).getDate()}`;
        const result = await fetchDeadlines(from, to);
        setDeadlines(result.deadlines);
        setTotal(result.total);
      } catch (err) {
        console.error("Failed to refetch deadlines:", err);
      }
    });
  }

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
        <Tabs value={view} onValueChange={handleViewChange}>
          <TabsList>
            <TabsTrigger value="list">List</TabsTrigger>
            <TabsTrigger value="calendar">Calendar</TabsTrigger>
            <TabsTrigger value="summary">Summary</TabsTrigger>
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
        {view === "list" && (
          <DeadlineListView
            deadlines={deadlines}
            slug={slug}
            selectedIds={selectedIds}
            onSelectionChange={setSelectedIds}
          />
        )}
        {view === "calendar" && (
          <DeadlineCalendarView
            deadlines={deadlines}
            year={year}
            month={month}
          />
        )}
        {view === "summary" && <DeadlineSummaryCards summaries={summaries} />}
      </div>

      {/* Batch filing actions */}
      <BatchFilingActions
        selectedIds={selectedIds}
        deadlines={deadlines}
        slug={slug}
        onClearSelection={() => setSelectedIds(new Set())}
        onFilingSuccess={refetchDeadlines}
      />
    </div>
  );
}
