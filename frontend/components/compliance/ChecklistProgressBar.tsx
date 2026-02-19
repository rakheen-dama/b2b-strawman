import { cn } from "@/lib/utils";

interface ChecklistProgressBarProps {
  completed: number;
  total: number;
  requiredCompleted: number;
  requiredTotal: number;
}

export function ChecklistProgressBar({
  completed,
  total,
  requiredCompleted,
  requiredTotal,
}: ChecklistProgressBarProps) {
  const percentage = total > 0 ? Math.round((completed / total) * 100) : 0;
  const allRequiredDone = requiredTotal > 0 && requiredCompleted >= requiredTotal;

  return (
    <div className="space-y-1">
      <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div
          className={cn(
            "h-full rounded-full transition-all",
            allRequiredDone ? "bg-emerald-500" : "bg-teal-500",
          )}
          style={{ width: `${percentage}%` }}
        />
      </div>
      <p
        className={cn(
          "text-xs",
          allRequiredDone
            ? "text-emerald-600 dark:text-emerald-400"
            : "text-slate-500 dark:text-slate-400",
        )}
      >
        {completed}/{total} completed ({requiredCompleted}/{requiredTotal} required)
      </p>
    </div>
  );
}
