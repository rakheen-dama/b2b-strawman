import Link from "next/link";
import { DiaryEventRow } from "@/components/diary/diary-event-row";
import { addDays, UPCOMING_DIARY_DAYS } from "@/app/(app)/org/[slug]/diary/diary-types";
import type { DiaryEventsResult } from "@/app/(app)/org/[slug]/my-work/todays-diary";

/** Rows shown before "+n more" — the Diary owns the full window. */
const MAX_UPCOMING_ROWS = 5;

/** Shown by both Upcoming and Next deadline, so the two never disagree. */
export const UPCOMING_UNIDENTIFIED_COPY = "Couldn't identify your member profile.";
export const UPCOMING_LOAD_FAILED_COPY = "The upcoming diary could not be loaded.";

/**
 * The Diary's List view for the signed-in fee earner, opened at the month containing `from`. The
 * List view shows one calendar month (`?from=` snaps to the 1st), so the anchor decides which
 * rows are visible.
 */
export function upcomingDiaryHref(slug: string, from: string): string {
  return `/org/${slug}/diary?view=list&from=${from}&feeEarner=mine`;
}

interface UpcomingDiaryListProps {
  slug: string;
  /** Server "today" (yyyy-MM-dd) — the window starts the day after. */
  today: string;
  /** GET /api/diary from tomorrow through today + 14 for the current user, in server order. */
  events: DiaryEventsResult;
}

/**
 * My work's "Upcoming" list (Phase 88 G-D5): court dates, prescriptions, matter and task due dates
 * for the next 14 days where the user is the fee earner (task assignee, else matter lead). Each
 * row shows its date, because the rows span days.
 */
export function UpcomingDiaryList({ slug, today, events }: UpcomingDiaryListProps) {
  const rows = Array.isArray(events) ? events : [];
  const hiddenCount = rows.length - MAX_UPCOMING_ROWS;

  return (
    <div data-testid="upcoming-diary" className="min-w-0">
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <p className="text-[11px] font-medium tracking-wider text-slate-500 uppercase">
          Upcoming · next {UPCOMING_DIARY_DAYS} days
        </p>
        <Link
          href={upcomingDiaryHref(slug, addDays(today, 1))}
          className="text-xs text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"
        >
          Open diary list
        </Link>
      </div>
      {events === "unidentified" ? (
        <p className="text-sm text-slate-400">{UPCOMING_UNIDENTIFIED_COPY}</p>
      ) : events === null ? (
        <p className="text-sm text-slate-400">{UPCOMING_LOAD_FAILED_COPY}</p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-slate-400">
          Nothing in the diary for the next {UPCOMING_DIARY_DAYS} days
        </p>
      ) : (
        <ul className="space-y-0.5">
          {rows.slice(0, MAX_UPCOMING_ROWS).map((event) => (
            <li key={`${event.kind}:${event.id}`}>
              <DiaryEventRow event={event} slug={slug} showDate />
            </li>
          ))}
        </ul>
      )}
      {hiddenCount > 0 && (
        // Opens the month of the first hidden row, so that row is on screen.
        <Link
          href={upcomingDiaryHref(slug, rows[MAX_UPCOMING_ROWS].date)}
          className="mt-2 inline-block text-xs text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"
        >
          +{hiddenCount} more
        </Link>
      )}
    </div>
  );
}
