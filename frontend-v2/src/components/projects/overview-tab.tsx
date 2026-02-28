import type { Task, Customer, ProjectMember, ProjectTimeSummary, BudgetStatusResponse } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { KpiStrip } from "@/components/layout/kpi-strip";
import { formatDuration, formatCurrencySafe } from "@/lib/format";
import { CheckCircle2, Clock, ListTodo, Users } from "lucide-react";
import Link from "next/link";

interface OverviewTabProps {
  slug: string;
  projectId: string;
  tasks: Task[];
  members: ProjectMember[];
  customers: Customer[];
  timeSummary: ProjectTimeSummary;
  budgetStatus: BudgetStatusResponse | null;
}

export function OverviewTab({
  slug,
  projectId,
  tasks,
  members,
  customers,
  timeSummary,
  budgetStatus,
}: OverviewTabProps) {
  const doneTasks = tasks.filter((t) => t.status === "DONE").length;
  const openTasks = tasks.filter(
    (t) => t.status === "OPEN" || t.status === "IN_PROGRESS"
  ).length;

  const kpiItems = [
    {
      label: "Open Tasks",
      value: openTasks,
    },
    {
      label: "Done Tasks",
      value: `${doneTasks}/${tasks.length}`,
    },
    {
      label: "Total Time",
      value: formatDuration(timeSummary.totalMinutes),
    },
    {
      label: "Team Members",
      value: members.length,
    },
  ];

  if (budgetStatus) {
    kpiItems.push({
      label: "Budget Used",
      value: `${Math.round(budgetStatus.hoursConsumedPct)}%`,
    });
  }

  return (
    <div className="space-y-6">
      <KpiStrip items={kpiItems} />

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Unfinished tasks */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <ListTodo className="size-4 text-slate-500" />
              Open Tasks
            </CardTitle>
          </CardHeader>
          <CardContent>
            {tasks
              .filter(
                (t) => t.status === "OPEN" || t.status === "IN_PROGRESS"
              )
              .slice(0, 5)
              .map((task) => (
                <div
                  key={task.id}
                  className="flex items-center justify-between border-b border-slate-100 py-2 last:border-0"
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-slate-900">
                      {task.title}
                    </p>
                    {task.assigneeName && (
                      <p className="text-xs text-slate-500">
                        {task.assigneeName}
                      </p>
                    )}
                  </div>
                  <div className="ml-3 flex items-center gap-2">
                    <StatusBadge status={task.status} />
                    <StatusBadge status={task.priority} />
                  </div>
                </div>
              ))}
            {openTasks === 0 && (
              <p className="py-4 text-center text-sm text-slate-500">
                All tasks completed
              </p>
            )}
            {openTasks > 5 && (
              <p className="pt-2 text-center text-xs text-slate-500">
                +{openTasks - 5} more
              </p>
            )}
          </CardContent>
        </Card>

        {/* Team roster */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <Users className="size-4 text-slate-500" />
              Team
            </CardTitle>
          </CardHeader>
          <CardContent>
            {members.slice(0, 6).map((member) => (
              <div
                key={member.id}
                className="flex items-center justify-between border-b border-slate-100 py-2 last:border-0"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-900">
                    {member.name}
                  </p>
                  <p className="text-xs text-slate-500">{member.email}</p>
                </div>
                <StatusBadge status={member.projectRole} />
              </div>
            ))}
            {members.length === 0 && (
              <p className="py-4 text-center text-sm text-slate-500">
                No team members assigned
              </p>
            )}
          </CardContent>
        </Card>

        {/* Customer */}
        {customers.length > 0 && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">Customer</CardTitle>
            </CardHeader>
            <CardContent>
              <Link
                href={`/org/${slug}/customers/${customers[0].id}`}
                className="text-sm font-medium text-teal-600 hover:text-teal-700 hover:underline"
              >
                {customers[0].name}
              </Link>
              {customers[0].email && (
                <p className="mt-1 text-xs text-slate-500">
                  {customers[0].email}
                </p>
              )}
            </CardContent>
          </Card>
        )}

        {/* Time summary */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <Clock className="size-4 text-slate-500" />
              Time Summary
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Billable</span>
              <span className="font-mono tabular-nums text-slate-900">
                {formatDuration(timeSummary.billableMinutes)}
              </span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Non-billable</span>
              <span className="font-mono tabular-nums text-slate-900">
                {formatDuration(timeSummary.nonBillableMinutes)}
              </span>
            </div>
            <div className="flex justify-between border-t border-slate-100 pt-2 text-sm font-medium">
              <span className="text-slate-700">Total</span>
              <span className="font-mono tabular-nums text-slate-900">
                {formatDuration(timeSummary.totalMinutes)}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
