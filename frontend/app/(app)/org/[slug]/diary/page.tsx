import { Suspense } from "react";
import { DiaryDaySkeleton } from "@/components/diary/diary-day-view";
import { DiaryListSkeleton } from "@/components/diary/diary-list-view";
import { DiaryMonthSkeleton } from "@/components/diary/diary-month-view";
import { DiaryWeekSkeleton } from "@/components/diary/diary-week-view";
import { DiaryLoader } from "./diary-loader";
import {
  diaryWindow,
  isValidIsoDate,
  parseDiaryKindsParam,
  parseDiaryView,
  toIsoDate,
  type DiaryView,
} from "./diary-types";

function DiarySkeleton({ view }: { view: DiaryView }) {
  switch (view) {
    case "day":
      return <DiaryDaySkeleton />;
    case "week":
      return <DiaryWeekSkeleton />;
    case "month":
      return <DiaryMonthSkeleton />;
    case "list":
      return <DiaryListSkeleton />;
  }
}

/**
 * One Diary (Phase 87 613B/613C) — court dates, prescription dates, matter due dates and task due
 * dates. `?view=day|week|month|list` (default week); `?from=` snaps to the view's window (day =
 * as-is, week = Monday, month / list = the 1st); `?types=` (chip preselection), `?feeEarner=`
 * (`mine` | `all` | member id) and `?matter=` are client-side filters seeded from the URL.
 * Reachable by URL only until 613D adds the nav item.
 */
export default async function DiaryPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{
    view?: string;
    from?: string;
    types?: string;
    feeEarner?: string;
    matter?: string;
  }>;
}) {
  const { slug } = await params;
  const search = await searchParams;

  const today = toIsoDate(new Date());
  const view = parseDiaryView(search.view);
  const { from, to } = diaryWindow(view, isValidIsoDate(search.from) ? search.from : today);
  const initialKinds = parseDiaryKindsParam(search.types);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Diary</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Court dates, prescription dates, matter due dates and task due dates.
        </p>
      </div>
      {/* Keyed on the whole window: Week → Day on a Monday keeps `from` but changes `to`. */}
      <Suspense key={`${from}_${to}`} fallback={<DiarySkeleton view={view} />}>
        <DiaryLoader
          slug={slug}
          today={today}
          from={from}
          to={to}
          view={view}
          initialKinds={initialKinds}
          initialFeeEarner={search.feeEarner ?? null}
          initialMatterId={search.matter ?? null}
        />
      </Suspense>
    </div>
  );
}
