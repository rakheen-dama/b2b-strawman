import { getAuthContext } from "@/lib/auth";
import { getTeamUtilization } from "@/lib/api/capacity";
import type { TeamUtilizationResponse } from "@/lib/api/capacity";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { UtilizationTable } from "@/components/capacity/utilization-table";
import { UtilizationChart } from "@/components/capacity/utilization-chart";
import { WeekRangeSelector } from "@/components/capacity/week-range-selector";
import { getCurrentMonday, formatDate, addWeeks } from "@/lib/date-utils";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

function computeWeekCount(weekStart: string, weekEnd: string): number {
  const start = new Date(weekStart);
  const end = new Date(weekEnd);
  const diffMs = end.getTime() - start.getTime();
  return Math.round(diffMs / (7 * 24 * 60 * 60 * 1000));
}

export default async function UtilizationPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ weekStart?: string; weekEnd?: string }>;
}) {
  const { slug } = await params;
  const sp = await searchParams;
  await getAuthContext();

  // Server-side module gate — short-circuit BEFORE invoking backend data fetches.
  if (!(await isModuleEnabledServer("resource_planning"))) {
    return <ModuleDisabledFallback moduleName="Resource Planning" slug={slug} />;
  }

  const defaultMonday = getCurrentMonday();
  const weekStart = sp.weekStart ?? formatDate(defaultMonday);
  const weekEnd = sp.weekEnd ?? formatDate(addWeeks(defaultMonday, 4));
  const weekCount = computeWeekCount(weekStart, weekEnd);

  let data: TeamUtilizationResponse = {
    members: [],
    teamAverages: {
      avgPlannedUtilizationPct: 0,
      avgActualUtilizationPct: 0,
      avgBillableUtilizationPct: 0,
    },
  };

  try {
    data = await getTeamUtilization(weekStart, weekEnd);
  } catch (err) {
    console.error("Failed to load team utilization", err);
  }

  return (
    <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
              Utilization
            </h1>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Team utilization metrics and trends
            </p>
          </div>
          <WeekRangeSelector weekStart={weekStart} weekCount={weekCount} />
        </div>

        <UtilizationTable data={data} slug={slug} />

      <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <UtilizationChart data={data} />
      </div>
    </div>
  );
}
