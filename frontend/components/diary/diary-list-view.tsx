import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { Skeleton } from "@b2mash/ui/skeleton";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import {
  formatDiaryDay,
  formatDiaryTime,
  groupByDate,
  humaniseSubtype,
  type DiaryEventResponse,
} from "@/app/(app)/org/[slug]/diary/diary-types";
import { DIARY_KIND_SINGULAR, DiaryKindIcon, kindRailClass } from "./diary-event-row";

interface DiaryListViewProps {
  /** In-window events, already filtered, in server order. */
  events: DiaryEventResponse[];
  /** Filtered overdue band (events dated before the window). */
  band: DiaryEventResponse[];
  slug: string;
}

/** Sentence-case column heads (the shared TableHead defaults to tracked-out caps). */
const HEAD = "normal-case tracking-normal";
const COLUMNS = ["Time", "Event", "Matter", "Fee earner"] as const;

function GroupHeading({
  label,
  tone = "default",
}: {
  label: string;
  tone?: "default" | "overdue";
}) {
  return (
    <TableRow className="hover:bg-transparent">
      <TableHead
        scope="rowgroup"
        colSpan={COLUMNS.length}
        className={cn(
          HEAD,
          "bg-slate-50 text-sm font-semibold dark:bg-slate-900/50",
          tone === "overdue" ? "text-destructive" : "text-slate-900 dark:text-slate-100"
        )}
      >
        {label}
      </TableHead>
    </TableRow>
  );
}

function EventRow({
  event,
  slug,
  showDate,
}: {
  event: DiaryEventResponse;
  slug: string;
  showDate: boolean;
}) {
  const time = formatDiaryTime(event.time);
  const subtype = event.kind === "COURT_DATE" ? humaniseSubtype(event.subtype) : null;
  const reference = event.matterReference ?? null;
  const isTask = event.kind === "TASK_DUE";

  return (
    <TableRow data-kind={event.kind} data-testid={`diary-list-row-${event.id}`}>
      <TableCell className={cn("w-28 align-top", kindRailClass(event.kind))}>
        <span className="flex flex-col">
          {showDate && (
            <span className="text-xs text-slate-600 dark:text-slate-400">
              {formatDiaryDay(event.date)}
            </span>
          )}
          {time && (
            <span className="font-mono text-xs text-slate-600 tabular-nums dark:text-slate-400">
              {time}
            </span>
          )}
        </span>
      </TableCell>
      <TableCell className="align-top whitespace-normal">
        <Link
          href={`/org/${slug}${event.href}`}
          data-testid={`diary-event-${event.id}`}
          className="focus-visible:ring-ring flex items-start gap-2 rounded-sm outline-none hover:underline focus-visible:ring-2"
        >
          <DiaryKindIcon kind={event.kind} className="mt-0.5" />
          <span className="sr-only">{DIARY_KIND_SINGULAR[event.kind]}</span>
          <span
            className={cn(
              "font-medium",
              isTask ? "text-muted-foreground" : "text-slate-900 dark:text-slate-100"
            )}
          >
            {event.title}
          </span>
        </Link>
        {(subtype || event.overdue) && (
          <span className="mt-1 ml-6 flex flex-wrap items-center gap-2 text-xs text-slate-600 dark:text-slate-400">
            {subtype && <span>{subtype}</span>}
            {event.overdue && <Badge variant="destructive">Overdue</Badge>}
          </span>
        )}
      </TableCell>
      <TableCell className="align-top whitespace-normal">
        <span className="block text-slate-900 dark:text-slate-100">{event.matterName}</span>
        {reference && <span className="text-muted-foreground block text-xs">{reference}</span>}
      </TableCell>
      <TableCell className="text-muted-foreground align-top">
        {event.feeEarnerName ?? null}
      </TableCell>
    </TableRow>
  );
}

function ListHeader() {
  return (
    <TableHeader>
      <TableRow className="hover:bg-transparent">
        {COLUMNS.map((c) => (
          <TableHead key={c} scope="col" className={HEAD}>
            {c}
          </TableHead>
        ))}
      </TableRow>
    </TableHeader>
  );
}

/** List view — the Overdue band first, then rows grouped by date (server order kept). */
export function DiaryListView({ events, band, slug }: DiaryListViewProps) {
  const byDate = groupByDate(events);

  return (
    <Table data-testid="diary-list">
      <TableCaption className="sr-only">Diary events</TableCaption>
      <ListHeader />
      {band.length > 0 && (
        <TableBody data-testid="diary-list-group-overdue">
          <GroupHeading label="Overdue" tone="overdue" />
          {band.map((event) => (
            <EventRow key={event.id} event={event} slug={slug} showDate />
          ))}
        </TableBody>
      )}
      {[...byDate.entries()].map(([iso, dayEvents]) => (
        <TableBody key={iso} data-testid={`diary-list-group-${iso}`}>
          <GroupHeading label={formatDiaryDay(iso, "long")} />
          {dayEvents.map((event) => (
            <EventRow key={event.id} event={event} slug={slug} showDate={false} />
          ))}
        </TableBody>
      ))}
    </Table>
  );
}

/** Loading shape for the List view: the same four columns with placeholder rows. */
export function DiaryListSkeleton() {
  return (
    <Table data-testid="diary-list-skeleton" aria-busy="true">
      <TableCaption className="sr-only">Loading diary events</TableCaption>
      <ListHeader />
      <TableBody>
        {Array.from({ length: 6 }, (_, i) => (
          <TableRow key={i} className="hover:bg-transparent">
            <TableCell className="w-28">
              <Skeleton className="h-4 w-12" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-48" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-32" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-24" />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
