"use client";

import { Card } from "@/components/ui/card";
import { DonutChart } from "@/components/dashboard/donut-chart";
import type { PersonalProjectBreakdown } from "@/lib/dashboard-types";

const PROJECT_COLORS = [
  "var(--color-chart-1, #2563eb)",
  "var(--color-chart-2, #e11d48)",
  "var(--color-chart-3, #e77e23)",
  "var(--color-chart-4, #8b5cf6)",
  "var(--color-chart-5, #06b6d4)",
  "var(--color-chart-6, #94a3b8)",
];

interface TimeBreakdownProps {
  data: PersonalProjectBreakdown[] | null;
}

export function TimeBreakdown({ data }: TimeBreakdownProps) {
  if (!data) {
    return (
      <Card data-testid="time-activity-panel">
        <div className="px-4 py-3">
          <h3 className="font-semibold text-slate-900 dark:text-slate-100">
            Time Breakdown
          </h3>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Unable to load time breakdown. Please try again.
          </p>
        </div>
      </Card>
    );
  }

  if (data.length === 0) {
    return (
      <Card data-testid="time-activity-panel">
        <div className="px-4 py-3">
          <h3 className="font-semibold text-slate-900 dark:text-slate-100">
            Time Breakdown
          </h3>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            No time logged this period.
          </p>
        </div>
      </Card>
    );
  }

  // Take top 4 projects and aggregate the rest as "Other"
  const sorted = [...data].sort((a, b) => b.hours - a.hours);
  const top4 = sorted.slice(0, 4);
  const rest = sorted.slice(4);

  const items = [...top4];

  if (rest.length > 0) {
    const otherHours = rest.reduce((sum, p) => sum + p.hours, 0);
    const otherPercent = rest.reduce((sum, p) => sum + p.percent, 0);
    items.push({
      projectId: "other",
      projectName: "Other",
      hours: otherHours,
      percent: otherPercent,
    });
  }

  // Transform to DonutChart data format
  const chartData = items.map((item, idx) => ({
    name: item.projectName,
    value: item.hours,
    color: PROJECT_COLORS[idx % PROJECT_COLORS.length],
  }));

  const totalHours = items.reduce((sum, p) => sum + p.hours, 0);

  return (
    <Card data-testid="time-activity-panel">
      <div className="px-4 py-3">
        <h3 className="font-semibold text-slate-900 dark:text-slate-100">
          Time Breakdown
        </h3>
        <div className="mt-2">
          <DonutChart
            data={chartData}
            centerValue={`${totalHours.toFixed(1)}h`}
            centerLabel="total"
            height={180}
          />
        </div>
        <div className="mt-2 space-y-1">
          {items.map((item, idx) => (
            <div
              key={item.projectId}
              className="flex items-center justify-between text-sm"
            >
              <div className="flex items-center gap-2">
                <span
                  className="inline-block size-2.5 rounded-full"
                  style={{
                    backgroundColor:
                      PROJECT_COLORS[idx % PROJECT_COLORS.length],
                  }}
                />
                <span className="text-slate-700 dark:text-slate-300">
                  {item.projectName}
                </span>
              </div>
              <span className="text-slate-600 dark:text-slate-400">
                {item.hours.toFixed(1)}h ({Math.round(item.percent)}%)
              </span>
            </div>
          ))}
        </div>
      </div>
    </Card>
  );
}
