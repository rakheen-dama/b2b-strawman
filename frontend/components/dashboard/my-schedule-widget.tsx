"use client";

import { Calendar, Clock } from "lucide-react";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import type { AllocationResponse, LeaveBlockResponse } from "@/lib/api/capacity";

interface MyScheduleWidgetProps {
  allocations: AllocationResponse[] | null;
  leaveBlocks: LeaveBlockResponse[] | null;
  weeklyCapacity: number;
  projectNames?: Record<string, string>;
}

function formatLeaveRange(start: string, end: string): string {
  const s = new Date(start + "T00:00:00");
  const e = new Date(end + "T00:00:00");
  const fmt = (d: Date) => d.toLocaleDateString("en-GB", { day: "numeric", month: "short" });
  if (start === end) return fmt(s);
  return `${fmt(s)} - ${fmt(e)}`;
}

export function MyScheduleWidget({
  allocations,
  leaveBlocks,
  weeklyCapacity,
  projectNames = {},
}: MyScheduleWidgetProps) {
  if (allocations === null) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Calendar className="h-4 w-4" />
            My Schedule
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm italic">Unable to load schedule data.</p>
        </CardContent>
      </Card>
    );
  }

  const totalAllocated = allocations.reduce((sum, a) => sum + a.allocatedHours, 0);
  const remaining = weeklyCapacity - totalAllocated;
  const isOverAllocated = remaining < 0;

  // Filter upcoming leave blocks (next 4 weeks)
  const now = new Date();
  const fourWeeksLater = new Date(now);
  fourWeeksLater.setDate(fourWeeksLater.getDate() + 28);
  const upcomingLeave = (leaveBlocks ?? []).filter((lb) => {
    const end = new Date(lb.endDate + "T00:00:00");
    return end >= now && new Date(lb.startDate + "T00:00:00") <= fourWeeksLater;
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Calendar className="h-4 w-4" />
          My Schedule
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* This week's allocations */}
        {allocations.length === 0 ? (
          <p className="text-muted-foreground text-sm italic">No allocations this week.</p>
        ) : (
          <div className="space-y-2">
            {allocations.map((a) => (
              <div key={a.id} className="flex items-center justify-between text-sm">
                <span className="truncate text-slate-700 dark:text-slate-300">
                  {projectNames[a.projectId] ?? a.projectId}
                </span>
                <span className="font-mono text-slate-500 tabular-nums dark:text-slate-400">
                  {a.allocatedHours}h
                </span>
              </div>
            ))}
          </div>
        )}

        {/* Capacity remaining / over-allocation warning */}
        <div
          className={`flex items-center gap-2 rounded-md px-3 py-2 ${
            isOverAllocated ? "bg-red-50 dark:bg-red-900/20" : "bg-slate-50 dark:bg-slate-800/50"
          }`}
        >
          <Clock className={`h-3.5 w-3.5 ${isOverAllocated ? "text-red-500" : "text-slate-400"}`} />
          <p
            className={`text-sm ${
              isOverAllocated
                ? "font-medium text-red-600 dark:text-red-400"
                : "text-slate-600 dark:text-slate-400"
            }`}
          >
            {isOverAllocated ? (
              <>
                Over by{" "}
                <span className="font-mono font-semibold tabular-nums">{Math.abs(remaining)}h</span>
              </>
            ) : (
              <>
                <span className="font-mono font-semibold tabular-nums">{remaining}h</span> capacity
                remaining
              </>
            )}
          </p>
        </div>

        {/* Upcoming leave */}
        {upcomingLeave.length > 0 && (
          <div className="space-y-1">
            <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
              Upcoming Leave
            </p>
            {upcomingLeave.map((lb) => (
              <p key={lb.id} className="text-sm text-slate-600 dark:text-slate-400">
                {formatLeaveRange(lb.startDate, lb.endDate)}
                {lb.note && (
                  <span className="ml-1 text-slate-400 dark:text-slate-500">&mdash; {lb.note}</span>
                )}
              </p>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
