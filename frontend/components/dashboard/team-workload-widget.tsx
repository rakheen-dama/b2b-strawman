"use client";

import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { HorizontalBarChart } from "@/components/dashboard/horizontal-bar-chart";
import type { TeamWorkloadEntry } from "@/lib/dashboard-types";

interface TeamWorkloadWidgetProps {
  data: TeamWorkloadEntry[] | null;
  isAdmin: boolean;
}

const DEFAULT_CHART_COLORS = [
  "#2563eb",
  "#e11d48",
  "#e77e23",
  "#8b5cf6",
  "#06b6d4",
];

function transformWorkloadData(entries: TeamWorkloadEntry[]) {
  // Collect all unique project names for consistent coloring across members
  const projectColorMap: Record<string, string> = {};
  let colorIdx = 0;
  for (const entry of entries) {
    for (const project of entry.projects) {
      if (!projectColorMap[project.projectName]) {
        projectColorMap[project.projectName] =
          DEFAULT_CHART_COLORS[colorIdx % DEFAULT_CHART_COLORS.length];
        colorIdx++;
      }
    }
  }

  return entries.map((entry) => ({
    label: entry.memberName,
    segments: entry.projects.map((p) => ({
      label: p.projectName,
      value: p.hours,
      color: projectColorMap[p.projectName],
    })),
  }));
}

export function TeamWorkloadWidget({ data, isAdmin }: TeamWorkloadWidgetProps) {
  if (data === null) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Team Workload</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground italic">
            Unable to load team workload. Please try again.
          </p>
        </CardContent>
      </Card>
    );
  }

  if (data.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Team Workload</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground italic">
            No time logged this period.
          </p>
        </CardContent>
      </Card>
    );
  }

  const chartData = transformWorkloadData(data);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Team Workload</CardTitle>
      </CardHeader>
      <CardContent>
        <HorizontalBarChart data={chartData} showLegend />
        {!isAdmin && (
          <p className="mt-3 text-xs text-muted-foreground">
            Contact an admin to see team-wide data.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
