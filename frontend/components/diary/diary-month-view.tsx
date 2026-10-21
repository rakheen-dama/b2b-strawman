"use client";

import { CircleAlert } from "lucide-react";
import { Card } from "@b2mash/ui/card";
import { Skeleton } from "@b2mash/ui/skeleton";
import { cn } from "@/lib/utils";
import {
  formatDiaryDay,
  formatDiaryMonth,
  formatDiaryTime,
  groupByDate,
  monthGridWeeks,
  parseIsoDate,
  type DiaryEventResponse,
} from "@/app/(app)/org/[slug]/diary/diary-types";
import { DIARY_KIND_SINGULAR, DiaryKindIcon, kindRailClass } from "./diary-event-row";
import { DiaryOverdueStrip } from "./diary-overdue-strip";
import { useDiaryGridNav } from "./use-diary-grid-nav";

interface DiaryMonthViewProps {
  /** In-window events, already filtered, in server order. */
  events: DiaryEventResponse[];
  /** Filtered overdue band (events dated before the 1st). */
  band: DiaryEventResponse[];
  /** 1st of the month (yyyy-MM-dd). */
  from: string;
  today: string;
  slug: string;
  /** A day cell was clicked or activated (Enter / Space) — open that day. */
  onSelectDay: (iso: string) => void;
}

/** Chips shown per day before the cell collapses the rest into "+n". */
export const MONTH_CHIP_LIMIT = 3;

const WEEKDAYS = [
  ["Mon", "Monday"],
  ["Tue", "Tuesday"],
  ["Wed", "Wednesday"],
  ["Thu", "Thursday"],
  ["Fri", "Friday"],
  ["Sat", "Saturday"],
  ["Sun", "Sunday"],
] as const;

const GRID_ROW = "grid grid-cols-7 gap-1";
const CELL_HEIGHT = "min-h-28";
/** Narrow screens scroll the month sideways rather than crushing seven columns. */
const SCROLLER = "overflow-x-auto";
const GRID_WIDTH = "min-w-[44rem]";

function WeekdayHeader() {
  return (
    <div role="row" className={GRID_ROW}>
      {WEEKDAYS.map(([short, long]) => (
        <div
          key={short}
          role="columnheader"
          className="px-1.5 pb-1 text-xs font-medium text-slate-600 dark:text-slate-400"
        >
          <span aria-hidden="true">{short}</span>
          <span className="sr-only">{long}</span>
        </div>
      ))}
    </div>
  );
}

function MonthChip({ event }: { event: DiaryEventResponse }) {
  const time = formatDiaryTime(event.time);
  return (
    <span
      data-kind={event.kind}
      data-testid={`diary-month-chip-${event.id}`}
      className={cn(
        "flex min-w-0 items-center gap-1 rounded-sm py-0.5 pr-1 pl-1 text-xs",
        kindRailClass(event.kind)
      )}
    >
      {event.overdue && (
        <>
          <CircleAlert aria-hidden="true" className="text-destructive size-4 shrink-0" />
          <span className="sr-only">Overdue</span>
        </>
      )}
      <DiaryKindIcon kind={event.kind} />
      <span className="sr-only">{DIARY_KIND_SINGULAR[event.kind]}</span>
      {time && (
        <span className="font-mono text-slate-600 tabular-nums dark:text-slate-400">{time}</span>
      )}
      <span
        className={cn(
          "truncate",
          event.kind === "TASK_DUE" ? "text-muted-foreground" : "text-slate-900 dark:text-slate-100"
        )}
      >
        {event.title}
      </span>
    </span>
  );
}

/**
 * Month view — Mon–Sun rows for the calendar month as a `role="grid"` with a roving tab stop.
 * Each day shows up to three single-line chips (kind rail, icon and hidden kind text — never colour
 * alone) and "+n" for the rest; the chips are not links, the whole day is: click it (or press Enter
 * / Space on it) to open the Day view. Days outside the month are empty, dimmed and unfocusable.
 */
export function DiaryMonthView({
  events,
  band,
  from,
  today,
  slug,
  onSelectDay,
}: DiaryMonthViewProps) {
  const byDate = groupByDate(events);
  const weeks = monthGridWeeks(from);
  const { cellProps } = useDiaryGridNav({
    cells: weeks.flat(),
    columns: 7,
    initial: today,
    onActivate: onSelectDay,
  });

  return (
    <div className="space-y-4">
      <DiaryOverdueStrip band={band} slug={slug} />

      <div className={SCROLLER}>
        <div
          role="grid"
          aria-label={formatDiaryMonth(from)}
          data-testid="diary-month-grid"
          className={cn("flex flex-col gap-1", GRID_WIDTH)}
        >
          <WeekdayHeader />
          {weeks.map((week) => (
            <div key={week.find(Boolean)} role="row" className={GRID_ROW}>
              {week.map((iso, i) => {
                if (iso === null) {
                  return (
                    <div
                      key={`blank-${i}`}
                      role="gridcell"
                      aria-disabled="true"
                      data-testid="diary-month-blank"
                      className={cn(CELL_HEIGHT, "rounded-lg bg-slate-50 dark:bg-slate-900/40")}
                    />
                  );
                }
                const isToday = iso === today;
                const dayEvents = byDate.get(iso) ?? [];
                const shown = dayEvents.slice(0, MONTH_CHIP_LIMIT);
                const more = dayEvents.length - shown.length;
                return (
                  <Card
                    key={iso}
                    role="gridcell"
                    data-testid={`diary-month-day-${iso}`}
                    aria-current={isToday ? "date" : undefined}
                    onClick={() => onSelectDay(iso)}
                    className={cn(
                      CELL_HEIGHT,
                      "focus-visible:ring-ring cursor-pointer gap-1 p-1.5 outline-none hover:bg-slate-50 focus-visible:ring-2 dark:hover:bg-slate-800/50"
                    )}
                    {...cellProps(iso)}
                  >
                    <span
                      aria-hidden="true"
                      className={cn(
                        "px-1 text-sm tabular-nums",
                        isToday
                          ? "font-semibold text-teal-600"
                          : "font-medium text-slate-700 dark:text-slate-300"
                      )}
                    >
                      {parseIsoDate(iso).getDate()}
                    </span>
                    <span className="sr-only">{formatDiaryDay(iso, "long")}</span>
                    {shown.map((event) => (
                      <MonthChip key={event.id} event={event} />
                    ))}
                    {more > 0 && (
                      <span
                        className="text-muted-foreground px-1 text-xs"
                        data-testid={`diary-month-more-${iso}`}
                      >
                        +{more}
                        <span className="sr-only"> more events</span>
                      </span>
                    )}
                  </Card>
                );
              })}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/** Loading shape for the Month view: weekday header and five rows of day cells. */
export function DiaryMonthSkeleton() {
  return (
    <div className={SCROLLER}>
      <div
        className={cn("flex flex-col gap-1", GRID_WIDTH)}
        data-testid="diary-month-skeleton"
        aria-busy="true"
      >
        <div className={GRID_ROW}>
          {WEEKDAYS.map(([short]) => (
            <Skeleton key={short} className="mx-1.5 mb-1 h-3 w-8" />
          ))}
        </div>
        {Array.from({ length: 5 }, (_, row) => (
          <div key={row} className={GRID_ROW}>
            {Array.from({ length: 7 }, (_, col) => (
              <Card
                key={col}
                className={cn(CELL_HEIGHT, "gap-1.5 p-1.5")}
                data-testid="diary-month-skeleton-day"
              >
                <Skeleton className="h-4 w-5" />
                {(row + col) % 3 === 0 && <Skeleton className="h-4 w-full" />}
              </Card>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
