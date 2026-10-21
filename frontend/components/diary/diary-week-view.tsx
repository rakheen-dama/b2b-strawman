import { Card } from "@b2mash/ui/card";
import { Skeleton } from "@b2mash/ui/skeleton";
import { cn } from "@/lib/utils";
import {
  addDays,
  formatDiaryDay,
  groupByDate,
  type DiaryEventResponse,
} from "@/app/(app)/org/[slug]/diary/diary-types";
import { DiaryEventRow } from "./diary-event-row";

interface DiaryWeekViewProps {
  /** In-window events, already filtered, in server order. */
  events: DiaryEventResponse[];
  /** Filtered overdue band (events dated before `from`). */
  band: DiaryEventResponse[];
  /** Monday of the week (yyyy-MM-dd). */
  from: string;
  today: string;
  slug: string;
}

function weekDays(from: string): string[] {
  return Array.from({ length: 7 }, (_, i) => addDays(from, i));
}

const WEEK_GRID = "grid grid-cols-1 gap-2 md:grid-cols-7";
const DAY_CARD = "gap-0 py-0 md:min-h-24";

/** Week view — Mon–Sun day cells, with an Overdue strip above when the band has rows. */
export function DiaryWeekView({ events, band, from, today, slug }: DiaryWeekViewProps) {
  const byDate = groupByDate(events);

  return (
    <div className="space-y-4">
      {band.length > 0 && (
        <section aria-labelledby="diary-overdue-week">
          <Card className="gap-0 py-0">
            <h2
              id="diary-overdue-week"
              className="text-destructive border-b border-slate-200/80 px-3 py-2 text-sm font-semibold dark:border-slate-800/80"
            >
              Overdue
            </h2>
            <div className="grid grid-cols-1 gap-x-4 gap-y-1 p-2 md:grid-cols-2 xl:grid-cols-3">
              {band.map((event) => (
                <DiaryEventRow key={event.id} event={event} slug={slug} showDate />
              ))}
            </div>
          </Card>
        </section>
      )}

      <div className={WEEK_GRID} data-testid="diary-week-grid">
        {weekDays(from).map((iso) => {
          const isToday = iso === today;
          const dayEvents = byDate.get(iso) ?? [];
          return (
            <Card
              key={iso}
              data-testid={`diary-day-${iso}`}
              aria-current={isToday ? "date" : undefined}
              className={DAY_CARD}
            >
              <h2
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
