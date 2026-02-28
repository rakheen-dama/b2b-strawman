"use client";

import type {
  Task,
  ProjectTimeSummary,
  MemberTimeSummary,
  TaskTimeSummary,
} from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDuration } from "@/lib/format";
import { LogTimeDialog } from "@/components/tasks/log-time-dialog";
import { Clock, Users } from "lucide-react";

interface TimeSummaryTabProps {
  projectId: string;
  slug: string;
  tasks: Task[];
  timeSummary: ProjectTimeSummary;
  timeSummaryByTask: TaskTimeSummary[];
  timeSummaryByMember: MemberTimeSummary[] | null;
  onCreateTimeEntry: (
    slug: string,
    projectId: string,
    taskId: string,
    formData: FormData
  ) => Promise<{ success: boolean; error?: string }>;
}

export function TimeSummaryTab({
  projectId,
  slug,
  tasks,
  timeSummary,
  timeSummaryByTask,
  timeSummaryByMember,
  onCreateTimeEntry,
}: TimeSummaryTabProps) {
  return (
    <div className="space-y-6">
      {/* Summary cards */}
      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="pt-6">
            <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Total Time
            </p>
            <p className="mt-1 font-mono text-2xl font-semibold tabular-nums text-slate-900">
              {formatDuration(timeSummary.totalMinutes)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Billable
            </p>
            <p className="mt-1 font-mono text-2xl font-semibold tabular-nums text-emerald-600">
              {formatDuration(timeSummary.billableMinutes)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Non-Billable
            </p>
            <p className="mt-1 font-mono text-2xl font-semibold tabular-nums text-slate-600">
              {formatDuration(timeSummary.nonBillableMinutes)}
            </p>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* By Task */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <Clock className="size-4 text-slate-500" />
              Time by Task
            </CardTitle>
          </CardHeader>
          <CardContent>
            {timeSummaryByTask.length === 0 ? (
              <p className="py-4 text-center text-sm text-slate-500">
                No time entries yet
              </p>
            ) : (
              <div className="space-y-2">
                {timeSummaryByTask.map((item) => (
                  <div
                    key={item.taskId}
                    className="flex items-center justify-between border-b border-slate-100 py-2 last:border-0"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-900">
                        {item.taskTitle}
                      </p>
                      <p className="text-xs text-slate-500">
                        {item.entryCount}{" "}
                        {item.entryCount === 1 ? "entry" : "entries"}
                      </p>
                    </div>
                    <div className="ml-3 text-right">
                      <p className="font-mono text-sm tabular-nums text-slate-900">
                        {formatDuration(item.totalMinutes)}
                      </p>
                      <p className="font-mono text-xs tabular-nums text-emerald-600">
                        {formatDuration(item.billableMinutes)} billable
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* By Member */}
        {timeSummaryByMember && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <Users className="size-4 text-slate-500" />
                Time by Member
              </CardTitle>
            </CardHeader>
            <CardContent>
              {timeSummaryByMember.length === 0 ? (
                <p className="py-4 text-center text-sm text-slate-500">
                  No time entries yet
                </p>
              ) : (
                <div className="space-y-2">
                  {timeSummaryByMember.map((item) => (
                    <div
                      key={item.memberId}
                      className="flex items-center justify-between border-b border-slate-100 py-2 last:border-0"
                    >
                      <p className="truncate text-sm font-medium text-slate-900">
                        {item.memberName}
                      </p>
                      <div className="ml-3 text-right">
                        <p className="font-mono text-sm tabular-nums text-slate-900">
                          {formatDuration(item.totalMinutes)}
                        </p>
                        <p className="font-mono text-xs tabular-nums text-emerald-600">
                          {formatDuration(item.billableMinutes)} billable
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        )}
      </div>

      {/* Quick log time for open tasks */}
      {tasks.filter((t) => t.status === "OPEN" || t.status === "IN_PROGRESS")
        .length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Quick Log Time</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {tasks
                .filter(
                  (t) =>
                    t.status === "OPEN" || t.status === "IN_PROGRESS"
                )
                .slice(0, 5)
                .map((task) => (
                  <LogTimeDialog
                    key={task.id}
                    slug={slug}
                    projectId={projectId}
                    taskId={task.id}
                    taskTitle={task.title}
                    onLogTime={onCreateTimeEntry}
                  />
                ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
