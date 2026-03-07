import { getAuthContext } from "@/lib/auth";
import { getTeamCapacityGrid } from "@/lib/api/capacity";
import type { TeamCapacityGrid } from "@/lib/api/capacity";
import { AllocationGrid } from "@/components/capacity/allocation-grid";
import { WeekRangeSelector } from "@/components/capacity/week-range-selector";
import { getCurrentMonday, formatDate, addWeeks } from "@/lib/date-utils";

function computeWeekCount(weekStart: string, weekEnd: string): number {
  const start = new Date(weekStart);
  const end = new Date(weekEnd);
  const diffMs = end.getTime() - start.getTime();
  return Math.round(diffMs / (7 * 24 * 60 * 60 * 1000));
}

export default async function ResourcesPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ weekStart?: string; weekEnd?: string }>;
}) {
  const { slug: _slug } = await params;
  const sp = await searchParams;
  await getAuthContext();

  const defaultMonday = getCurrentMonday();
  const weekStart = sp.weekStart ?? formatDate(defaultMonday);
  const weekEnd = sp.weekEnd ?? formatDate(addWeeks(defaultMonday, 4));
  const weekCount = computeWeekCount(weekStart, weekEnd);

  let grid: TeamCapacityGrid = { members: [], weekSummaries: [] };

  try {
    grid = await getTeamCapacityGrid(weekStart, weekEnd);
  } catch (err) {
    console.error("Failed to load team capacity grid", err);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Resources
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Team capacity planning and allocation overview
          </p>
        </div>
        <WeekRangeSelector weekStart={weekStart} weekCount={weekCount} />
      </div>

      <AllocationGrid grid={grid} />
    </div>
  );
}
