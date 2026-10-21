import { Suspense } from "react";
import { DiaryListSkeleton } from "@/components/diary/diary-list-view";
import { DiaryWeekSkeleton } from "@/components/diary/diary-week-view";
import { DiaryLoader } from "./diary-loader";
import {
  addDays,
  isValidIsoDate,
  mondayOf,
  parseDiaryKindsParam,
  parseDiaryView,
  toIsoDate,
} from "./diary-types";

/**
 * One Diary (Phase 87 613B) — court dates, prescription dates, matter due dates and task due
 * dates for one Mon–Sun week. `?from=` (any day → its Monday), `?view=week|list`, `?types=` (chip
 * preselection, applied client-side). Reachable by URL only until 613D adds the nav item.
 */
export default async function DiaryPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ view?: string; from?: string; types?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;

  const today = toIsoDate(new Date());
  const view = parseDiaryView(search.view);
  const from = mondayOf(isValidIsoDate(search.from) ? search.from : today);
  const to = addDays(from, 6);
  const initialKinds = parseDiaryKindsParam(search.types);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Diary</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Court dates, prescription dates, matter due dates and task due dates for the week.
        </p>
      </div>
      <Suspense
        key={from}
        fallback={view === "list" ? <DiaryListSkeleton /> : <DiaryWeekSkeleton />}
      >
        <DiaryLoader
          slug={slug}
          today={today}
          from={from}
          to={to}
          view={view}
          initialKinds={initialKinds}
        />
      </Suspense>
    </div>
  );
}
