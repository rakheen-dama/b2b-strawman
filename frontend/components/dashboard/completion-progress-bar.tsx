import { cn } from "@/lib/utils";

interface CompletionProgressBarProps {
  percent: number;
}

function getBarColor(percent: number): string {
  if (percent > 66) return "bg-green-500";
  if (percent > 33) return "bg-amber-500";
  return "bg-red-500";
}

export function CompletionProgressBar({ percent }: CompletionProgressBarProps) {
  const clampedPercent = Math.max(0, Math.min(100, percent));

  return (
    <div className="flex items-center gap-2">
      <div className="relative h-2 flex-1 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
        <div
          className={cn("h-full rounded-full transition-all", getBarColor(clampedPercent))}
          style={{ width: `${clampedPercent}%` }}
        />
      </div>
      <span className="text-xs font-medium text-muted-foreground tabular-nums">
        {Math.round(clampedPercent)}%
      </span>
    </div>
  );
}
