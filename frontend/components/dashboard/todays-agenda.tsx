import Link from "next/link";
import { cn } from "@/lib/utils";
import { Badge } from "@b2mash/ui/badge";
import { formatDuration } from "@/lib/format";
import { DiaryEventRow } from "@/components/diary/diary-event-row";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";
import type { MyWorkTimeEntryItem } from "@/lib/types";
import type { PersonalDeadline } from "@/lib/dashboard-types";

/** Rows shown before "+n more" — the Diary owns the full day. */
const MAX_DIARY_ROWS = 5;

/**
 * GET /api/diary?from=today&to=today for the current user (Phase 87 613D): today's rows plus the
 * open-overdue band (rows dated before today). `"unidentified"` = the signed-in user couldn't be
 * matched to a member, so there is no personal diary to show; `null` = the diary could not be
 * loaded.
 */
export type TodaysDiaryEvents = DiaryEventResponse[] | "unidentified" | null;

interface TodaysAgendaProps {
  slug: string;
  /** Server "today" (yyyy-MM-dd) — the date the diary was fetched for. */
  today: string;
  diaryEvents: TodaysDiaryEvents;
  todayEntries: MyWorkTimeEntryItem[];
  upcomingDeadlines: PersonalDeadline[];
  weeklyCapacityHours?: number;
}

function getDaysRemaining(dueDate: string): number {
  const now = new Date();
  const todayDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const [year, month, day] = dueDate.split("-").map(Number);
  const due = new Date(year, month - 1, day);
  return Math.ceil((due.getTime() - todayDate.getTime()) / (1000 * 60 * 60 * 24));
}

interface TodaysDiaryProps {
  slug: string;
  /** Server "today" (yyyy-MM-dd) — the date the diary was fetched for. */
  today: string;
  diaryEvents: TodaysDiaryEvents;
}

/** Today's diary on the Diary's Day view — the "Open diary" target on My work and Home. */
export function todaysDiaryHref(slug: string): string {
  return `/org/${slug}/diary?view=day`;
}

/**
 * Today's diary (613D) as the My work agenda's first section: an eyebrow label + "Open diary",
 * then the {@link TodaysDiaryList}. Home (614B) puts the same list in its own card instead.
 */
export function TodaysDiary({ slug, today, diaryEvents }: TodaysDiaryProps) {
  return (
    <div data-testid="todays-diary" className="min-w-0">
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <p className="text-[11px] font-medium tracking-wider text-slate-500 uppercase">
          Today&apos;s diary
        </p>
        <Link
          href={todaysDiaryHref(slug)}
          className="text-xs text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"
        >
          Open diary
        </Link>
      </div>
      <TodaysDiaryList slug={slug} today={today} diaryEvents={diaryEvents} />
    </div>
  );
}

/**
 * Today's rows (≤ 5, then "+n more") plus an "N overdue" link for the open-overdue band, or the
 * unidentified / failed / empty line. No heading — the caller supplies it.
 */
export function TodaysDiaryList({ slug, today, diaryEvents }: TodaysDiaryProps) {
  // The backend returns today's rows plus the overdue band (dated < today).
  const events = Array.isArray(diaryEvents) ? diaryEvents : [];
  const todaysEvents = events.filter((e) => e.date === today);
  const overdueCount = events.filter((e) => e.date < today).length;
  const hiddenCount = todaysEvents.length - MAX_DIARY_ROWS;
  const diaryHref = todaysDiaryHref(slug);

  return (
    <>
      {diaryEvents === "unidentified" ? (
        <p className="text-sm text-slate-400">
          Couldn&apos;t identify your member profile.{" "}
          <Link
            href={`/org/${slug}/diary`}
            className="underline hover:text-slate-900 dark:hover:text-slate-100"
          >
            Open the Diary
          </Link>
        </p>
      ) : diaryEvents === null ? (
        <p className="text-sm text-slate-400">Today&apos;s diary could not be loaded.</p>
      ) : todaysEvents.length === 0 ? (
        <p className="text-sm text-slate-400">Nothing in the diary today</p>
      ) : (
        <ul className="space-y-0.5">
          {todaysEvents.slice(0, MAX_DIARY_ROWS).map((event) => (
            <li key={`${event.kind}:${event.id}`}>
              <DiaryEventRow event={event} slug={slug} />
            </li>
          ))}
        </ul>
      )}
      {(hiddenCount > 0 || overdueCount > 0) && (
        <div className="mt-2 flex flex-wrap items-center gap-3">
          {hiddenCount > 0 && (
            <Link
              href={diaryHref}
              className="text-xs text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"
            >
              +{hiddenCount} more
            </Link>
          )}
          {overdueCount > 0 && (
            <Link
              href={diaryHref}
              data-testid="todays-diary-overdue"
              className="focus-visible:ring-ring rounded-full outline-none focus-visible:ring-2"
            >
              <Badge variant="destructive">{overdueCount} overdue</Badge>
            </Link>
          )}
        </div>
      )}
    </>
  );
}

export function TodaysAgenda({
  slug,
  today,
  diaryEvents,
  todayEntries,
  upcomingDeadlines,
  weeklyCapacityHours = 40,
}: TodaysAgendaProps) {
  const dailyTargetMinutes = (weeklyCapacityHours / 5) * 60; // e.g. 480 min = 8h

  // Today's logged minutes
  const loggedMinutes = todayEntries.reduce((sum, e) => sum + e.durationMinutes, 0);
  const progressPct = Math.min(100, (loggedMinutes / dailyTargetMinutes) * 100);

  // Next deadline — first entry (already sorted by backend)
  const nextDeadline = upcomingDeadlines[0] ?? null;

  return (
    <div className="bg-card rounded-lg border p-4">
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {/* Section 1: Today's diary — court dates, prescriptions, matter and task due dates */}
        <TodaysDiary slug={slug} today={today} diaryEvents={diaryEvents} />

        {/* Section 2: Time Logged Today */}
        <div data-testid="time-progress-today">
          <p className="mb-2 text-[11px] font-medium tracking-wider text-slate-500 uppercase">
            Time Today
          </p>
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="font-mono font-medium text-slate-900 tabular-nums">
                {formatDuration(loggedMinutes)}
              </span>
              <span className="font-mono text-xs text-slate-400 tabular-nums">
                / {formatDuration(dailyTargetMinutes)}
              </span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-slate-200">
              <div
                className="h-full rounded-full bg-teal-500 transition-all"
                style={{ width: `${progressPct}%` }}
              />
            </div>
          </div>
        </div>

        {/* Section 3: Next Deadline */}
        <div data-testid="next-deadline">
          <p className="mb-2 text-[11px] font-medium tracking-wider text-slate-500 uppercase">
            Next Deadline
          </p>
          {!nextDeadline ? (
            <p className="text-sm text-slate-400">No upcoming deadlines</p>
          ) : (
            <div>
              <p className="truncate text-sm font-medium text-slate-900">{nextDeadline.taskName}</p>
              <p className="truncate text-xs text-slate-500">{nextDeadline.matterName}</p>
              {(() => {
                const days = getDaysRemaining(nextDeadline.dueDate);
                const colorClass =
                  days < 0 ? "text-red-600" : days <= 2 ? "text-amber-600" : "text-slate-600";
                return (
                  <span className={cn("text-xs font-medium", colorClass)}>
                    {days < 0
                      ? `${Math.abs(days)}d overdue`
                      : days === 0
                        ? "Due today"
                        : `${days}d remaining`}
                  </span>
                );
              })()}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
