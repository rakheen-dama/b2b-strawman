"use client";

import { useState, useTransition, useCallback } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatDuration } from "@/lib/format";
import { fetchMyTimeSummary } from "@/app/(app)/org/[slug]/my-work/actions";
import type { MyWorkTimeSummary } from "@/lib/types";

// --- Date helpers ---

/** Returns Sunday of the week starting at the given Monday. */
function getSunday(monday: Date): Date {
  const d = new Date(monday);
  d.setDate(monday.getDate() + 6);
  return d;
}

/** Format a date as YYYY-MM-DD for API calls. */
function toIsoDate(date: Date): string {
  return date.toLocaleDateString("en-CA");
}

/** Format the week range label, e.g. "Feb 10 - Feb 16, 2026". */
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

// --- Component ---

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
        const result = await fetchMyTimeSummary(from, to);
        setSummary(result);
      });
    },
    [weekStart]
  );

  const isEmpty = !summary || summary.totalMinutes === 0;

  return (
    <div className="rounded-lg border border-olive-200 bg-white p-6 dark:border-olive-800 dark:bg-olive-950">
      {/* Week Navigation Header */}
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-olive-900 dark:text-olive-100">
          This Week
        </h2>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="size-7"
            onClick={() => navigateWeek(-1)}
            disabled={isPending}
            aria-label="Previous week"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <span className="min-w-[140px] text-center text-xs text-olive-600 dark:text-olive-400">
            {isPending ? "Loading..." : weekLabel}
          </span>
          <Button
            variant="ghost"
            size="icon"
            className="size-7"
            onClick={() => navigateWeek(1)}
            disabled={isPending}
            aria-label="Next week"
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>

      {isEmpty ? (
        <p className="mt-4 text-sm text-olive-500 dark:text-olive-400">
          No time tracked this week
        </p>
      ) : (
        <div className="mt-4 space-y-4">
          {/* Stat Cards: Total, Billable, Non-billable */}
          <div className="space-y-3">
            <div className="rounded-md bg-olive-50 px-4 py-3 dark:bg-olive-900">
              <p className="text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                Total
              </p>
              <p className="font-display text-2xl text-olive-900 dark:text-olive-100">
                {formatDuration(summary!.totalMinutes)}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-md bg-olive-50 px-4 py-3 dark:bg-olive-900">
                <p className="text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Billable
                </p>
                <p className="font-display text-xl text-green-600 dark:text-green-400">
                  {formatDuration(summary!.billableMinutes)}
                </p>
              </div>
              <div className="rounded-md bg-olive-50 px-4 py-3 dark:bg-olive-900">
                <p className="text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Non-billable
                </p>
                <p className="font-display text-xl text-olive-600 dark:text-olive-400">
                  {formatDuration(summary!.nonBillableMinutes)}
                </p>
              </div>
            </div>
          </div>

          {/* By Project Breakdown */}
          {summary!.byProject.length > 0 && (
            <div className="border-t border-olive-200 pt-4 dark:border-olive-800">
              <h3 className="mb-3 text-sm font-medium text-olive-700 dark:text-olive-300">
                By Project
              </h3>
              <div className="space-y-2">
                {summary!.byProject.map((project) => (
                  <div
                    key={project.projectId}
                    className="flex items-center justify-between text-sm"
                  >
                    <span className="truncate text-olive-700 dark:text-olive-300">
                      {project.projectName}
                    </span>
                    <div className="flex shrink-0 items-center gap-3">
                      <span className="text-xs text-green-600 dark:text-green-400">
                        {formatDuration(project.billableMinutes)}
                      </span>
                      <span className="font-medium text-olive-900 dark:text-olive-100">
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
    </div>
  );
}
