"use client";

import { BarChart3 } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { EmptyState } from "@/components/empty-state";
import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/skeleton";

const HorizontalBarChart = dynamic(
  () =>
    import("@/components/dashboard/horizontal-bar-chart").then(
      (mod) => mod.HorizontalBarChart,
    ),
  { loading: () => <Skeleton className="h-48 w-full" />, ssr: false },
);

const DonutChart = dynamic(
  () =>
    import("@/components/dashboard/donut-chart").then(
      (mod) => mod.DonutChart,
    ),
  { loading: () => <Skeleton className="h-48 w-full" />, ssr: false },
);

import type { TeamWorkloadEntry } from "@/lib/dashboard-types";

interface TeamWorkloadWidgetProps {
  data: TeamWorkloadEntry[] | null;
  isAdmin: boolean;
}

const WEEKLY_HOURS = 40;

function getUtilizationColor(pct: number): string {
  if (pct > 100) return "var(--color-red-500, #ef4444)";
  if (pct > 85) return "var(--color-amber-500, #f59e0b)";
  return "var(--color-teal-500, #14b8a6)";
}

function transformUtilizationData(entries: TeamWorkloadEntry[]) {
  return entries.map((entry) => {
    const pct = Math.round((entry.totalHours / WEEKLY_HOURS) * 100);
    return {
      label: entry.memberName,
      segments: [
        {
          label: `${pct}%`,
          value: entry.totalHours,
          color: getUtilizationColor(pct),
        },
      ],
    };
  });
}

function aggregateProjectHours(
  entries: TeamWorkloadEntry[],
): Array<{ name: string; value: number }> {
  const projectMap: Record<string, number> = {};
  for (const entry of entries) {
    for (const project of entry.projects) {
      projectMap[project.projectName] =
        (projectMap[project.projectName] ?? 0) + project.hours;
    }
  }

  const sorted = Object.entries(projectMap)
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value);

  if (sorted.length <= 5) return sorted;

  const top5 = sorted.slice(0, 5);
  const otherTotal = sorted.slice(5).reduce((sum, p) => sum + p.value, 0);
  return [...top5, { name: "Other", value: otherTotal }];
}

export function TeamWorkloadWidget({
  data,
  isAdmin,
}: TeamWorkloadWidgetProps) {
  if (data === null) {
    return (
      <Card data-testid="team-time-panel">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium">Team Time</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm italic text-slate-500">
            Unable to load team workload. Please try again.
          </p>
        </CardContent>
      </Card>
    );
  }

  if (data.length === 0) {
    return (
      <Card data-testid="team-time-panel">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium">Team Time</CardTitle>
        </CardHeader>
        <CardContent>
          <EmptyState
            icon={BarChart3}
            title="No time logged"
            description="Log time against tasks to see team workload distribution."
          />
        </CardContent>
      </Card>
    );
  }

  const chartData = transformUtilizationData(data);
  const totalHours = data.reduce((sum, e) => sum + e.totalHours, 0);
  const donutData = aggregateProjectHours(data);

  return (
    <Card data-testid="team-time-panel">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">Team Time</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4 pt-0">
        <HorizontalBarChart
          data={chartData}
          maxValue={WEEKLY_HOURS}
          referenceLine={WEEKLY_HOURS * 0.8}
        />
        <DonutChart
          data={donutData}
          centerValue={`${totalHours.toFixed(0)}h`}
          centerLabel="total"
          height={180}
        />
        {!isAdmin && (
          <p className="text-xs text-slate-500">
            Contact an admin to see team-wide data.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
