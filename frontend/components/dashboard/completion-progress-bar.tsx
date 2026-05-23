"use client";

import { cn } from "@/lib/utils";

interface CompletionProgressBarProps {
  percent: number;
}

function getBarClasses(percent: number): string {
  if (percent > 66) return "from-emerald-400 to-emerald-500";
  if (percent > 33) return "from-amber-400 to-amber-500";
  return "from-red-400 to-red-500";
}

function getTrackColor(percent: number): string {
  if (percent > 66) return "bg-emerald-100 dark:bg-emerald-950/40";
  if (percent > 33) return "bg-amber-100 dark:bg-amber-950/40";
  return "bg-red-100 dark:bg-red-950/40";
}

export function CompletionProgressBar({ percent }: CompletionProgressBarProps) {
  const clampedPercent = Math.max(0, Math.min(100, percent));

  return (
    <div className="flex items-center gap-2.5">
      <div
        className={cn(
          "relative h-2.5 flex-1 overflow-hidden rounded-full",
          getTrackColor(clampedPercent)
        )}
      >
        <div
          className={cn(
            "h-full rounded-full bg-gradient-to-r transition-all duration-500 ease-out",
            getBarClasses(clampedPercent)
          )}
          style={{ width: `${clampedPercent}%` }}
        />
      </div>
      <span className="text-muted-foreground w-8 text-right text-xs font-semibold tabular-nums">
        {Math.round(clampedPercent)}%
      </span>
    </div>
  );
}
