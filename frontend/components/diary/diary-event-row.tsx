import Link from "next/link";
import { AlarmClock, CircleCheck, Flag, Gavel, type LucideIcon } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { cn } from "@/lib/utils";
import {
  formatDiaryDay,
  formatDiaryTime,
  humaniseSubtype,
  type DiaryEventResponse,
  type DiaryKind,
} from "@/app/(app)/org/[slug]/diary/diary-types";

/**
 * One Diary kind encoding (arch §12.6.2): court date = solid teal rail + gavel; prescription =
 * destructive rail + alarm clock (the one place red is earned); matter due = dashed slate rail +
 * flag; task = no rail + muted circle-check. Kind is never colour-only — every row carries the
 * icon plus a visually hidden kind label.
 */

/** Singular kind name, used as the visually hidden label on each row. */
export const DIARY_KIND_SINGULAR: Record<DiaryKind, string> = {
  COURT_DATE: "Court date",
  PRESCRIPTION: "Prescription",
  MATTER_DUE: "Matter due",
  TASK_DUE: "Task due",
};

const KIND_ICON: Record<DiaryKind, LucideIcon> = {
  COURT_DATE: Gavel,
  PRESCRIPTION: AlarmClock,
  MATTER_DUE: Flag,
  TASK_DUE: CircleCheck,
};

const KIND_ICON_CLASS: Record<DiaryKind, string> = {
  COURT_DATE: "text-teal-600",
  PRESCRIPTION: "text-destructive",
  MATTER_DUE: "text-slate-500 dark:text-slate-400",
  TASK_DUE: "text-muted-foreground",
};

/**
 * Left-rail classes for a kind. Only the left border has width, so `border-dashed` affects the
 * rail alone — never add an all-sides `border` to an element carrying these classes.
 */
export function kindRailClass(kind: DiaryKind): string {
  switch (kind) {
    case "COURT_DATE":
      return "border-l-[3px] border-l-teal-600";
    case "PRESCRIPTION":
      return "border-l-[3px] border-l-destructive";
    case "MATTER_DUE":
      return "border-l-[3px] border-dashed border-l-slate-400";
    case "TASK_DUE":
      // "No rail" — a transparent rail keeps text aligned with the other kinds.
      return "border-l-[3px] border-l-transparent";
  }
}

/** Kind icon (decorative — pair it with visible or visually hidden kind text). */
export function DiaryKindIcon({ kind, className }: { kind: DiaryKind; className?: string }) {
  const Icon = KIND_ICON[kind];
  return (
    <Icon aria-hidden="true" className={cn("size-4 shrink-0", KIND_ICON_CLASS[kind], className)} />
  );
}

interface DiaryEventRowProps {
  event: DiaryEventResponse;
  slug: string;
  /** Show the event's date (overdue band rows, which sit outside a day). */
  showDate?: boolean;
}

/** Compact diary row used by the Week view (day cells and the Overdue strip). */
export function DiaryEventRow({ event, slug, showDate = false }: DiaryEventRowProps) {
  const time = formatDiaryTime(event.time);
  const subtype = event.kind === "COURT_DATE" ? humaniseSubtype(event.subtype) : null;
  const reference = event.matterReference ?? null;
  const feeEarner = event.feeEarnerName ?? null;
  const isTask = event.kind === "TASK_DUE";

  return (
    <Link
      href={`/org/${slug}${event.href}`}
      data-kind={event.kind}
      data-testid={`diary-event-${event.id}`}
      className={cn(
        "focus-visible:ring-ring flex items-start gap-2 rounded-md py-1.5 pr-2 pl-2 text-sm outline-none hover:bg-slate-50 focus-visible:ring-2 dark:hover:bg-slate-800/50",
        kindRailClass(event.kind)
      )}
    >
      <DiaryKindIcon kind={event.kind} className="mt-0.5" />
      <span className="sr-only">{DIARY_KIND_SINGULAR[event.kind]}</span>
      <span className="min-w-0 flex-1">
        <span className="flex items-baseline gap-2">
          {time && (
            <span className="font-mono text-xs text-slate-600 tabular-nums dark:text-slate-400">
              {time}
            </span>
          )}
          <span
            className={cn(
              "truncate font-medium",
              isTask ? "text-muted-foreground" : "text-slate-900 dark:text-slate-100"
            )}
          >
            {event.title}
          </span>
        </span>
        <span className="text-muted-foreground mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
          {showDate && <span>{formatDiaryDay(event.date)}</span>}
          {subtype && <span>{subtype}</span>}
          <span className="truncate">{event.matterName}</span>
          {reference && <span>{reference}</span>}
          {feeEarner && <span>{feeEarner}</span>}
          {event.overdue && <Badge variant="destructive">Overdue</Badge>}
        </span>
      </span>
    </Link>
  );
}
