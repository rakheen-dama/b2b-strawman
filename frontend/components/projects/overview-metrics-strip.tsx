import { Card } from "@/components/ui/card";
import { MiniProgressRing } from "@/components/dashboard/mini-progress-ring";
import { CompletionProgressBar } from "@/components/dashboard/completion-progress-bar";
import type { ProjectHealthMetrics } from "@/lib/dashboard-types";
import type { MemberHoursEntry } from "@/lib/dashboard-types";
import type { BudgetStatusResponse } from "@/lib/types";

interface OverviewMetricsStripProps {
  metrics: ProjectHealthMetrics | null;
  memberHours: MemberHoursEntry[] | null;
  budgetStatus: BudgetStatusResponse | null;
  marginPercent: number | null;
  showMargin: boolean;
}

export function OverviewMetricsStrip({
  metrics,
  memberHours,
  budgetStatus,
  marginPercent,
  showMargin,
}: OverviewMetricsStripProps) {
  const tasksDone = metrics?.tasksDone ?? 0;
  const totalTasks = metrics?.totalTasks ?? 0;
  const completionPercent = metrics?.completionPercent ?? 0;

  const totalHours = memberHours?.reduce((sum, m) => sum + m.totalHours, 0) ?? 0;

  const budgetPercent = budgetStatus?.hoursConsumedPct ?? null;

  return (
    <div className="grid auto-rows-fr grid-cols-2 gap-3 sm:grid-cols-4">
      {/* Tasks */}
      <Card className="px-4 py-3">
        <span className="text-muted-foreground text-sm">Tasks</span>
        <div className="mt-1 flex items-center gap-2">
          <MiniProgressRing value={completionPercent} size={32} />
          <div>
            <span className="text-lg font-bold tabular-nums">
              {tasksDone}/{totalTasks}
            </span>
            <span className="text-muted-foreground ml-1 text-xs">complete</span>
          </div>
        </div>
      </Card>

      {/* Hours */}
      <Card className="px-4 py-3">
        <span className="text-muted-foreground text-sm">Hours</span>
        <div className="mt-1">
          <span className="text-lg font-bold tabular-nums">{totalHours.toFixed(1)}h</span>
          <span className="text-muted-foreground ml-1 text-xs">this month</span>
        </div>
      </Card>

      {/* Budget */}
      <Card className="px-4 py-3">
        <span className="text-muted-foreground text-sm">Budget</span>
        <div className="mt-1">
          {budgetPercent != null ? (
            <div className="space-y-1">
              <span className="text-lg font-bold tabular-nums">{Math.round(budgetPercent)}%</span>
              <span className="text-muted-foreground ml-1 text-xs">used</span>
              <CompletionProgressBar percent={budgetPercent} />
            </div>
          ) : (
            <span className="text-muted-foreground text-sm italic">No budget</span>
          )}
        </div>
      </Card>

      {/* Margin */}
      <Card className="px-4 py-3">
        <span className="text-muted-foreground text-sm">Margin</span>
        <div className="mt-1">
          {showMargin && marginPercent != null ? (
            <span className="text-lg font-bold tabular-nums">{marginPercent.toFixed(1)}%</span>
          ) : (
            <span className="text-muted-foreground text-sm italic">--</span>
          )}
        </div>
      </Card>
    </div>
  );
}
