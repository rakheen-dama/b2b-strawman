"use client";

import { cn } from "@/lib/utils";

interface RetainerProgressProps {
  type: "HOUR_BANK" | "FIXED_FEE";
  consumedHours: number;
  allocatedHours: number | null;
  className?: string;
}

export function RetainerProgress({
  type,
  consumedHours,
  allocatedHours,
  className,
}: RetainerProgressProps) {
  // FIXED_FEE: show consumed hours only, no bar
  if (type === "FIXED_FEE" || allocatedHours === null || allocatedHours <= 0) {
    return (
      <div
        className={cn(
          "text-sm text-slate-600 dark:text-slate-400",
          className,
        )}
      >
        {consumedHours.toFixed(1)} hrs consumed
      </div>
    );
  }

  const percent = (consumedHours / allocatedHours) * 100;
  const barWidth = Math.min(100, percent);

  // Color thresholds: green (< 80%), amber (80-99%), red (>= 100%)
  let barColor = "bg-green-500";
  if (percent >= 100) {
    barColor = "bg-red-500";
  } else if (percent >= 80) {
    barColor = "bg-amber-500";
  }

  return (
    <div className={cn("space-y-1", className)}>
      <div className="flex justify-between text-xs text-slate-600 dark:text-slate-400">
        <span>
          {consumedHours.toFixed(1)} of {allocatedHours.toFixed(1)} hrs
        </span>
        <span data-testid="progress-percent">{Math.round(percent)}%</span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div
          data-testid="progress-bar"
          className={cn("h-full rounded-full transition-all", barColor)}
          style={{ width: `${barWidth}%` }}
        />
      </div>
    </div>
  );
}
