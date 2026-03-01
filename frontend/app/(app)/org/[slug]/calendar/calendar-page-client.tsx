"use client";

import { useState, useTransition, useCallback } from "react";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CalendarMonthView } from "./calendar-month-view";
import { CalendarListView } from "./calendar-list-view";
import { getCalendarItems } from "./calendar-actions";
import type { CalendarItem } from "./calendar-actions";

interface CalendarPageClientProps {
  initialItems: CalendarItem[];
  initialOverdueCount: number;
  initialYear: number;
  initialMonth: number; // 1-indexed
  slug: string;
}

function formatDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${dd}`;
}

export function CalendarPageClient({
  initialItems,
  initialOverdueCount,
  initialYear,
  initialMonth,
  slug,
}: CalendarPageClientProps) {
  const [items, setItems] = useState(initialItems);
  const [overdueCount, setOverdueCount] = useState(initialOverdueCount);
  const [year, setYear] = useState(initialYear);
  const [month, setMonth] = useState(initialMonth);
  const [view, setView] = useState<"month" | "list">("month");
  const [isPending, startTransition] = useTransition();

  const navigateMonth = useCallback(
    (newYear: number, newMonth: number) => {
      setYear(newYear);
      setMonth(newMonth);

      const firstDay = new Date(newYear, newMonth - 1, 1);
      const lastDay = new Date(newYear, newMonth, 0);
      const from = formatDate(firstDay);
      const to = formatDate(lastDay);

      startTransition(async () => {
        const result = await getCalendarItems(from, to);
        setItems(result.items);
        setOverdueCount(result.overdueCount);
      });
    },
    []
  );

  return (
    <div className="space-y-4">
      {/* View Toggle */}
      <div className="flex items-center justify-between">
        <Tabs
          value={view}
          onValueChange={(v) => setView(v as "month" | "list")}
        >
          <TabsList>
            <TabsTrigger value="month">Month</TabsTrigger>
            <TabsTrigger value="list">List</TabsTrigger>
          </TabsList>
        </Tabs>
        {overdueCount > 0 && (
          <span className="text-sm text-red-600 dark:text-red-400">
            {overdueCount} overdue
          </span>
        )}
      </div>

      {/* Views */}
      {view === "month" ? (
        <CalendarMonthView
          items={items}
          year={year}
          month={month}
          onNavigate={navigateMonth}
          isPending={isPending}
        />
      ) : (
        <CalendarListView items={items} slug={slug} />
      )}
    </div>
  );
}
