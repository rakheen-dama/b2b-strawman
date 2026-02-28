import { Users } from "lucide-react";

import { WidgetCard } from "@/components/layout/widget-grid";
import { cn } from "@/lib/utils";
import type { TeamWorkloadEntry } from "@/lib/dashboard-types";

interface TeamWorkloadWidgetProps {
  data: TeamWorkloadEntry[] | null;
  isAdmin: boolean;
}

const BAR_COLORS = [
  "bg-blue-500",
  "bg-rose-500",
  "bg-amber-500",
  "bg-violet-500",
  "bg-teal-500",
];

export function TeamWorkloadWidget({
  data,
  isAdmin,
}: TeamWorkloadWidgetProps) {
  if (data === null) {
    return (
      <WidgetCard title="Team Workload">
        <p className="py-4 text-center text-sm text-slate-500 italic">
          Unable to load team workload.
        </p>
      </WidgetCard>
    );
  }

  if (data.length === 0) {
    return (
      <WidgetCard title="Team Workload">
        <div className="flex flex-col items-center gap-2 py-8 text-center">
          <Users className="size-8 text-slate-300 dark:text-slate-700" />
          <p className="text-sm text-slate-500">No time logged this period</p>
        </div>
      </WidgetCard>
    );
  }

  // Build a stable color map for projects across all members
  const projectColorMap: Record<string, string> = {};
  let colorIdx = 0;
  for (const entry of data) {
    for (const project of entry.projects) {
      if (!projectColorMap[project.projectName]) {
        projectColorMap[project.projectName] =
          BAR_COLORS[colorIdx % BAR_COLORS.length];
        colorIdx++;
      }
    }
  }

  const maxHours = Math.max(...data.map((e) => e.totalHours), 1);

  return (
    <WidgetCard title="Team Workload">
      <div className="space-y-3">
        {data.map((entry) => (
          <div key={entry.memberId} className="space-y-1">
            <div className="flex items-center justify-between text-sm">
              <span className="truncate font-medium text-slate-700 dark:text-slate-300">
                {entry.memberName}
              </span>
              <span className="ml-2 shrink-0 font-mono text-xs tabular-nums text-slate-500">
                {entry.totalHours.toFixed(1)}h
              </span>
            </div>
            <div className="flex h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
              {entry.projects.map((p) => {
                const widthPct = (p.hours / maxHours) * 100;
                return (
                  <div
                    key={p.projectId}
                    className={cn(
                      "h-full transition-all",
                      projectColorMap[p.projectName],
                    )}
                    style={{ width: `${widthPct}%` }}
                    title={`${p.projectName}: ${p.hours.toFixed(1)}h`}
                  />
                );
              })}
            </div>
          </div>
        ))}
      </div>

      {/* Legend */}
      <div className="mt-4 flex flex-wrap gap-3">
        {Object.entries(projectColorMap).map(([name, color]) => (
          <div key={name} className="flex items-center gap-1.5">
            <span className={cn("size-2 rounded-full", color)} />
            <span className="text-xs text-slate-500">{name}</span>
          </div>
        ))}
      </div>

      {!isAdmin && (
        <p className="mt-3 text-xs text-slate-500">
          Contact an admin to see team-wide data.
        </p>
      )}
    </WidgetCard>
  );
}
