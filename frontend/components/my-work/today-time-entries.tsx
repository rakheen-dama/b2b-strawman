"use client";

import { Clock } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { formatDuration } from "@/lib/format";
import type { MyWorkTimeEntryItem } from "@/lib/types";

// --- Component ---

interface TodayTimeEntriesProps {
  entries: MyWorkTimeEntryItem[];
}

export function TodayTimeEntries({ entries }: TodayTimeEntriesProps) {
  const totalMinutes = entries.reduce(
    (sum, entry) => sum + entry.durationMinutes,
    0
  );

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-slate-900 dark:text-slate-100">
          Today
        </h2>
        {totalMinutes > 0 && (
          <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
            {formatDuration(totalMinutes)}
          </span>
        )}
      </div>

      {entries.length === 0 ? (
        <div className="mt-4 flex flex-col items-center gap-2 py-8 text-center">
          <Clock className="size-8 text-slate-300 dark:text-slate-700" />
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No time logged today
          </p>
        </div>
      ) : (
        <div className="mt-4 space-y-3">
          {entries.map((entry) => (
            <div
              key={entry.id}
              className="flex items-start justify-between gap-3 rounded-md border border-slate-100 p-3 dark:border-slate-800"
            >
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-950 dark:text-slate-50">
                  {entry.taskTitle}
                </p>
                <p className="mt-0.5 truncate text-xs text-slate-500 dark:text-slate-400">
                  {entry.projectName}
                  {entry.description && ` \u2014 ${entry.description}`}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Badge variant={entry.billable ? "success" : "neutral"}>
                  {entry.billable ? "Billable" : "Non-billable"}
                </Badge>
                <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  {formatDuration(entry.durationMinutes)}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
