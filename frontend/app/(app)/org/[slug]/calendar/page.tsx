import { getCalendarItems } from "./calendar-actions";
import type { CalendarResponse } from "./calendar-types";
import { formatDate } from "./calendar-types";
import { CalendarPageClient } from "./calendar-page-client";

function getMonthDateRange(): {
  from: string;
  to: string;
  year: number;
  month: number;
} {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth(); // 0-indexed
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);

  return {
    from: formatDate(firstDay),
    to: formatDate(lastDay),
    year,
    month: month + 1,
  };
}

export default async function CalendarPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { from, to, year, month } = getMonthDateRange();

  let initialData: CalendarResponse = { items: [], overdueCount: 0 };
  try {
    initialData = await getCalendarItems(from, to);
  } catch (error) {
    console.error("Failed to fetch calendar items:", error);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Calendar
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          View upcoming due dates across all projects
        </p>
      </div>

      <CalendarPageClient
        initialItems={initialData.items}
        initialOverdueCount={initialData.overdueCount}
        initialYear={year}
        initialMonth={month}
        slug={slug}
      />
    </div>
  );
}
