import Link from "next/link";
import { DiaryEventRow } from "@/components/diary/diary-event-row";
import {
  UPCOMING_DIARY_DAYS,
  type DiaryEventResponse,
} from "@/app/(app)/org/[slug]/diary/diary-types";

/** Rows shown before "+n more" — the Diary owns the full window. */
const MAX_UPCOMING_ROWS = 5;

/** The Diary's List view filtered to the signed-in fee earner — "See all upcoming" / "+n more". */
export function upcomingDiaryHref(slug: string): string {
  return `/org/${slug}/diary?view=list&feeEarner=mine`;
}

interface UpcomingDiaryListProps {
  slug: string;
  /**
   * GET /api/diary from tomorrow through today + 14 for the current user, in server order.
   * `"unidentified"` = no member profile; `null` = could not be loaded.
   */
  events: DiaryEventResponse[] | "unidentified" | null;
}

/**
 * My work's "Upcoming" list (Phase 88 G-D5): court dates, prescriptions, matter and task due dates
 * for the next 14 days where the user is the fee earner (task assignee, else matter lead). Each
 * row shows its date, because the rows span days.
 */
export function UpcomingDiaryList({ slug, events }: UpcomingDiaryListProps) {
  const rows = Array.isArray(events) ? events : [];
  const hiddenCount = rows.length - MAX_UPCOMING_ROWS;
  const href = upcomingDiaryHref(slug);

  return (
    <div data-testid="upcoming-diary" className="min-w-0">
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <p className="text-[11px] font-medium tracking-wider text-slate-500 uppercase">
          Upcoming · next {UPCOMING_DIARY_DAYS} days
        </p>
        <Link
          href={href}
          className="text-xs text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"
        >
          See all upcoming
        </Link>
      </div>
      {events === "unidentified" ? (
        <p className="text-sm text-slate-400">Couldn&apos;t identify your member profile.</p>
      ) : events === null ? (
        <p className="text-sm text-slate-400">The upcoming diary could not be loaded.</p>
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
        <Link
          href={href}
          className="mt-2 inline-block text-xs text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"
        >
          +{hiddenCount} more
        </Link>
      )}
    </div>
  );
}
