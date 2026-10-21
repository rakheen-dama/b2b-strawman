import { Card } from "@b2mash/ui/card";
import { Skeleton } from "@b2mash/ui/skeleton";
import {
  DIARY_KINDS,
  DIARY_KIND_LABELS,
  type DiaryEventResponse,
  type DiaryKind,
} from "@/app/(app)/org/[slug]/diary/diary-types";
import { DiaryEventRow, DiaryKindIcon } from "./diary-event-row";
import { DiaryOverdueStrip } from "./diary-overdue-strip";

interface DiaryDayViewProps {
  /** In-window events for the one day, already filtered, in server order. */
  events: DiaryEventResponse[];
  /** Filtered overdue band (events dated before the day). */
  band: DiaryEventResponse[];
  slug: string;
}

function groupByKind(events: DiaryEventResponse[]): [DiaryKind, DiaryEventResponse[]][] {
  const groups: [DiaryKind, DiaryEventResponse[]][] = [];
  for (const kind of DIARY_KINDS) {
    const group = events.filter((e) => e.kind === kind);
    if (group.length > 0) groups.push([kind, group]);
  }
  return groups;
}

const SECTION_HEADING =
  "flex items-center gap-2 border-b border-slate-200/80 px-3 py-2 text-sm font-semibold text-slate-900 dark:border-slate-800/80 dark:text-slate-100";

/**
 * Day view — one date, grouped by kind in display order (court dates first: they cannot move;
 * then prescriptions, matter due dates, tasks). Semantic sections, headings and lists rather than
 * a grid: a single day has nothing to arrow between. The Overdue strip sits above.
 */
export function DiaryDayView({ events, band, slug }: DiaryDayViewProps) {
  const groups = groupByKind(events);

  return (
    <div className="space-y-4" data-testid="diary-day-view">
      <DiaryOverdueStrip band={band} slug={slug} />

      {groups.length === 0 ? (
        <p className="text-muted-foreground text-sm">Nothing is due on this day.</p>
      ) : (
        groups.map(([kind, group]) => {
          const headingId = `diary-day-kind-${kind}`;
          return (
            <section key={kind} aria-labelledby={headingId} data-testid={`diary-day-group-${kind}`}>
              <Card className="gap-0 py-0">
                <h2 id={headingId} className={SECTION_HEADING}>
                  <DiaryKindIcon kind={kind} />
                  <span>{DIARY_KIND_LABELS[kind]}</span>
                  <span className="text-muted-foreground font-normal tabular-nums">
                    {group.length}
                  </span>
                </h2>
                <ul className="flex flex-col gap-1 p-1.5">
                  {group.map((event) => (
                    <li key={event.id}>
                      <DiaryEventRow event={event} slug={slug} />
                    </li>
                  ))}
                </ul>
              </Card>
            </section>
          );
        })
      )}
    </div>
  );
}

/** Loading shape for the Day view: two kind sections with placeholder rows. */
export function DiaryDaySkeleton() {
  return (
    <div className="space-y-4" data-testid="diary-day-skeleton" aria-busy="true">
      {[3, 2].map((rows, i) => (
        <Card key={i} className="gap-0 py-0">
          <div className="border-b border-slate-200/80 px-3 py-2 dark:border-slate-800/80">
            <Skeleton className="h-4 w-28" />
          </div>
          <div className="flex flex-col gap-1.5 p-1.5">
            {Array.from({ length: rows }, (_, j) => (
              <Skeleton key={j} className="h-10 w-full" />
            ))}
          </div>
        </Card>
      ))}
    </div>
  );
}
