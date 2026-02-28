import { cn } from "@/lib/utils";

interface CompletionBarProps {
  percent: number;
  className?: string;
}

export function CompletionBar({ percent, className }: CompletionBarProps) {
  const clamped = Math.min(100, Math.max(0, percent));
  return (
    <div className={cn("flex items-center gap-2", className)}>
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div
          className={cn(
            "h-full rounded-full transition-all",
            clamped >= 75
              ? "bg-emerald-500"
              : clamped >= 40
                ? "bg-amber-500"
                : "bg-slate-400",
          )}
          style={{ width: `${clamped}%` }}
        />
      </div>
      <span className="text-xs font-medium tabular-nums text-slate-500">
        {Math.round(clamped)}%
      </span>
    </div>
  );
}
