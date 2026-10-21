"use client";

import { Card } from "@b2mash/ui/card";
import { Skeleton } from "@b2mash/ui/skeleton";
import { cn } from "@/lib/utils";
import {
  formatDiaryDate,
  formatDiaryDay,
  groupByDate,
  weekDays,
  type DiaryEventResponse,
} from "@/app/(app)/org/[slug]/diary/diary-types";
import { DiaryEventRow } from "./diary-event-row";
import { DiaryOverdueStrip } from "./diary-overdue-strip";
import { useDiaryGridNav } from "./use-diary-grid-nav";

interface DiaryWeekViewProps {
  /** In-window events, already filtered, in server order. */
  events: DiaryEventResponse[];
  /** Filtered overdue band (events dated before `from`). */
  band: DiaryEventResponse[];
  /** Monday of the week (yyyy-MM-dd). */
  from: string;
  today: string;
  slug: string;
  /** Enter / Space on a focused day cell — open that day. */
  onSelectDay?: (iso: string) => void;
}

const WEEK_GRID = "grid grid-cols-1 gap-2 md:grid-cols-7";
const DAY_CARD = "gap-0 py-0 md:min-h-24";

/**
 * Week view — Mon–Sun day cells as a one-row `role="grid"` with a roving tab stop (Arrow keys,
 * Home / End), and an Overdue strip above when the band has rows.
 */
export function DiaryWeekView({
  events,
  band,
  from,
  today,
  slug,
  onSelectDay,
}: DiaryWeekViewProps) {
  const byDate = groupByDate(events);
  const days = weekDays(from);
  const { cellProps } = useDiaryGridNav({
    cells: days,
    columns: 7,
    initial: today,
    onActivate: onSelectDay,
  });

  return (
    <div className="space-y-4">
      <DiaryOverdueStrip band={band} slug={slug} />

      <div role="grid" aria-label={`Week of ${formatDiaryDate(from)}`}>
        <div role="row" className={WEEK_GRID} data-testid="diary-week-grid">
          {days.map((iso) => {
            const isToday = iso === today;
            const dayEvents = byDate.get(iso) ?? [];
            const headingId = `diary-week-day-${iso}`;
            return (
              <Card
                key={iso}
                role="gridcell"
                aria-labelledby={headingId}
                data-testid={`diary-day-${iso}`}
                aria-current={isToday ? "date" : undefined}
                className={cn(
                  DAY_CARD,
                  "focus-visible:ring-ring outline-none focus-visible:ring-2"
                )}
                {...cellProps(iso)}
              >
                <h2
                  id={headingId}
                  className={cn(
                    "border-b border-slate-200/80 px-3 py-2 text-sm dark:border-slate-800/80",
                    isToday
                      ? "font-semibold text-teal-600"
                      : "font-medium text-slate-700 dark:text-slate-300"
                  )}
                >
                  {formatDiaryDay(iso)}
                </h2>
                {dayEvents.length > 0 && (
                  <div className="flex flex-col gap-1 p-1.5">
                    {dayEvents.map((event) => (
                      <DiaryEventRow key={event.id} event={event} slug={slug} />
                    ))}
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/** Loading shape for the Week view: seven day cells with placeholder rows. */
export function DiaryWeekSkeleton() {
  return (
    <div className={WEEK_GRID} data-testid="diary-week-skeleton" aria-busy="true">
      {Array.from({ length: 7 }, (_, i) => (
        <Card key={i} className={DAY_CARD} data-testid="diary-week-skeleton-day">
          <div className="border-b border-slate-200/80 px-3 py-2 dark:border-slate-800/80">
            <Skeleton className="h-4 w-16" />
          </div>
          <div className="flex flex-col gap-1.5 p-1.5">
            {Array.from({ length: (i % 3) + 1 }, (_, j) => (
              <Skeleton key={j} className="h-8 w-full" />
            ))}
          </div>
        </Card>
      ))}
    </div>
  );
}
