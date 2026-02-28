"use client";

import { useState, useTransition, useCallback } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { formatDuration } from "@/lib/format";
import { fetchMyTimeSummaryAction } from "@/lib/actions/my-work";
import type { MyWorkTimeSummary } from "@/lib/types";

/** Returns Sunday of the week starting at the given Monday. */
function getSunday(monday: Date): Date {
  const d = new Date(monday);
  d.setDate(monday.getDate() + 6);
  return d;
}

function toIsoDate(date: Date): string {
  return date.toLocaleDateString("en-CA");
}

function formatWeekLabel(monday: Date, sunday: Date): string {
  const from = monday.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
  const to = sunday.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
  return `${from} \u2013 ${to}`;
}

interface WeeklyTimeSummaryProps {
  initialSummary: MyWorkTimeSummary | null;
  initialFrom: string;
}

export function WeeklyTimeSummary({
  initialSummary,
  initialFrom,
}: WeeklyTimeSummaryProps) {
  const [summary, setSummary] = useState(initialSummary);
  const [weekStart, setWeekStart] = useState(() => {
    const [y, m, d] = initialFrom.split("-").map(Number);
    return new Date(y, m - 1, d);
  });
  const [isPending, startTransition] = useTransition();

  const weekEnd = getSunday(weekStart);
  const weekLabel = formatWeekLabel(weekStart, weekEnd);

  const navigateWeek = useCallback(
    (direction: -1 | 1) => {
      const newMonday = new Date(weekStart);
      newMonday.setDate(weekStart.getDate() + direction * 7);
      setWeekStart(newMonday);

      const newSunday = getSunday(newMonday);
      const from = toIsoDate(newMonday);
      const to = toIsoDate(newSunday);

      startTransition(async () => {
        const result = await fetchMyTimeSummaryAction(from, to);
        setSummary(result);
      });
    },
    [weekStart],
  );

  const isEmpty = !summary || summary.totalMinutes === 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium text-slate-700">
          This Week
        </CardTitle>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon-xs"
            onClick={() => navigateWeek(-1)}
            disabled={isPending}
            aria-label="Previous week"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <span className="min-w-[140px] text-center text-xs text-slate-600 dark:text-slate-400">
            {isPending ? "Loading..." : weekLabel}
          </span>
          <Button
            variant="ghost"
            size="icon-xs"
            onClick={() => navigateWeek(1)}
            disabled={isPending}
            aria-label="Next week"
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {isEmpty ? (
          <p className="py-4 text-sm text-slate-500">
            No time tracked this week
          </p>
        ) : (
          <div className="space-y-4">
            {/* Total */}
            <div className="rounded-md bg-slate-50 px-4 py-3 dark:bg-slate-900">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Total
              </p>
              <p className="font-display text-2xl text-slate-900 dark:text-slate-100">
                {formatDuration(summary!.totalMinutes)}
              </p>
            </div>

            {/* Billable / Non-billable */}
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-md bg-slate-50 px-4 py-3 dark:bg-slate-900">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Billable
                </p>
                <p className="font-display text-xl text-emerald-600 dark:text-emerald-400">
                  {formatDuration(summary!.billableMinutes)}
                </p>
              </div>
              <div className="rounded-md bg-slate-50 px-4 py-3 dark:bg-slate-900">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Non-billable
                </p>
                <p className="font-display text-xl text-slate-600 dark:text-slate-400">
                  {formatDuration(summary!.nonBillableMinutes)}
                </p>
              </div>
            </div>

            {/* By Project */}
            {summary!.byProject.length > 0 && (
              <div className="border-t border-slate-200 pt-4 dark:border-slate-800">
                <h3 className="mb-3 text-sm font-medium text-slate-700 dark:text-slate-300">
                  By Project
                </h3>
                <div className="space-y-2">
                  {summary!.byProject.map((project) => (
                    <div
                      key={project.projectId}
                      className="flex items-center justify-between text-sm"
                    >
                      <span className="truncate text-slate-700 dark:text-slate-300">
                        {project.projectName}
                      </span>
                      <div className="flex shrink-0 items-center gap-3">
                        <span className="text-xs text-emerald-600 dark:text-emerald-400">
                          {formatDuration(project.billableMinutes)}
                        </span>
                        <span className="font-medium text-slate-900 dark:text-slate-100">
                          {formatDuration(project.totalMinutes)}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
