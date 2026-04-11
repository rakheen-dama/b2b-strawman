"use client";

import { Clock, CheckCircle2, ArrowRight } from "lucide-react";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import type { KpiResponse, CrossProjectActivityItem } from "@/lib/dashboard-types";

interface MyWeekColumnProps {
  kpis: KpiResponse | null;
  activity: CrossProjectActivityItem[] | null;
}

export function MyWeekColumn({ kpis, activity }: MyWeekColumnProps) {
  const hoursToday = kpis?.totalHoursLogged
    ? Math.round((kpis.totalHoursLogged / 20) * 10) / 10 // approximate daily from monthly
    : 0;

  const tasksCompleted = activity
    ? activity.filter((a) => a.eventType === "task.completed" || a.eventType === "task.updated")
        .length
    : 0;

  const nextTask = activity?.find(
    (a) => a.eventType === "task.created" || a.eventType === "task.updated"
  );

  return (
    <Card data-testid="my-week-column">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">My Week</CardTitle>
      </CardHeader>
      <CardContent className="space-y-1 pt-0">
        <div className="flex items-center gap-3 rounded-md px-2 py-2">
          <Clock className="size-4 shrink-0 text-slate-400" />
          <span className="font-mono text-sm font-bold tabular-nums">{hoursToday}h</span>
          <span className="text-xs text-slate-500">Avg. daily hours</span>
        </div>

        <div className="flex items-center gap-3 rounded-md px-2 py-2">
          <CheckCircle2 className="size-4 shrink-0 text-teal-500" />
          <span className="font-mono text-sm font-bold tabular-nums">{tasksCompleted}</span>
          <span className="text-xs text-slate-500">Tasks this week</span>
        </div>

        {nextTask && (
          <div className="flex items-center gap-3 rounded-md px-2 py-2">
            <ArrowRight className="size-4 shrink-0 text-slate-400" />
            <span className="truncate text-xs text-slate-600 dark:text-slate-300">
              {nextTask.description}
            </span>
          </div>
        )}

        {!nextTask && (
          <div className="flex items-center gap-3 rounded-md px-2 py-2">
            <ArrowRight className="size-4 shrink-0 text-slate-400" />
            <span className="text-xs text-slate-500">No upcoming tasks</span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
