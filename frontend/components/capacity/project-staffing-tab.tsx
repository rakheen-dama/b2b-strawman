"use client";

import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { MiniProgressRing } from "@/components/dashboard/mini-progress-ring";
import type { ProjectStaffingResponse } from "@/lib/api/capacity";

interface ProjectStaffingTabProps {
  staffing: ProjectStaffingResponse | null;
}

export function ProjectStaffingTab({ staffing }: ProjectStaffingTabProps) {
  if (staffing === null) {
    return (
      <div className="space-y-6">
        <p className="text-muted-foreground text-sm italic">Unable to load staffing data.</p>
      </div>
    );
  }

  if (staffing.members.length === 0) {
    return (
      <div className="space-y-6">
        <p className="text-muted-foreground text-sm italic">
          No team members allocated to this project.
        </p>
      </div>
    );
  }

  // Collect all unique week starts across all members
  const weekStarts = Array.from(
    new Set(staffing.members.flatMap((m) => m.weeks.map((w) => w.weekStart)))
  ).sort();

  const formatWeekLabel = (weekStart: string) => {
    const d = new Date(weekStart + "T00:00:00");
    return d.toLocaleDateString("en-GB", { day: "numeric", month: "short" });
  };

  return (
    <div className="space-y-6">
      {/* Allocated Members Table */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Allocated Members</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800">
                  <th className="py-2 pr-4 text-left font-medium text-slate-600 dark:text-slate-400">
                    Member
                  </th>
                  {weekStarts.map((ws) => (
                    <th
                      key={ws}
                      className="px-3 py-2 text-center font-medium text-slate-600 dark:text-slate-400"
                    >
                      {formatWeekLabel(ws)}
                    </th>
                  ))}
                  <th className="py-2 pl-4 text-right font-medium text-slate-600 dark:text-slate-400">
                    Total
                  </th>
                </tr>
              </thead>
              <tbody>
                {staffing.members.map((member) => (
                  <tr
                    key={member.memberId}
                    className="border-b border-slate-100 dark:border-slate-800/50"
                  >
                    <td className="py-2 pr-4 text-slate-900 dark:text-slate-100">
                      {member.memberName}
                    </td>
                    {weekStarts.map((ws) => {
                      const weekCell = member.weeks.find((w) => w.weekStart === ws);
                      const hours = weekCell?.allocatedHours ?? 0;
                      return (
                        <td
                          key={ws}
                          className="px-3 py-2 text-center font-mono text-slate-600 tabular-nums dark:text-slate-400"
                        >
                          {hours > 0 ? `${hours}h` : "-"}
                        </td>
                      );
                    })}
                    <td className="py-2 pl-4 text-right font-mono font-semibold text-slate-900 tabular-nums dark:text-slate-100">
                      {member.totalAllocatedHours}h
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Budget Comparison */}
      {staffing.budgetHours !== null && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Planned Hours vs Budget</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center gap-4">
              <MiniProgressRing value={staffing.budgetUsedPct ?? 0} size={56} />
              <div>
                <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  <span className="font-mono tabular-nums">{staffing.totalPlannedHours}h</span> of{" "}
                  <span className="font-mono tabular-nums">{staffing.budgetHours}h</span> budget
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  <span className="font-mono tabular-nums">
                    {Math.round(staffing.budgetUsedPct ?? 0)}%
                  </span>{" "}
                  utilized
                </p>
              </div>
            </div>
            {(staffing.budgetUsedPct ?? 0) > 100 && (
              <Badge variant="destructive" className="text-xs">
                Over budget by {staffing.totalPlannedHours - (staffing.budgetHours ?? 0)}h
              </Badge>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
