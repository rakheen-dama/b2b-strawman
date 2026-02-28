"use client";

import { cn } from "@/lib/utils";

interface ProgressGaugeProps {
  consumed: number;
  allocated: number;
  label?: string;
  className?: string;
}

export function ProgressGauge({
  consumed,
  allocated,
  label,
  className,
}: ProgressGaugeProps) {
  const percentage =
    allocated > 0 ? Math.round((consumed / allocated) * 100) : 0;
  const isOverage = percentage > 100;
  const displayPct = Math.min(percentage, 100);

  // Determine color
  let barColor = "bg-teal-500";
  if (percentage >= 90 && percentage <= 100) {
    barColor = "bg-amber-500";
  } else if (isOverage) {
    barColor = "bg-red-500";
  }

  return (
    <div className={cn("space-y-2", className)}>
      {label && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-slate-600">{label}</span>
          <span
            className={cn(
              "font-mono font-semibold tabular-nums",
              isOverage ? "text-red-600" : "text-slate-900",
            )}
          >
            {percentage}%
          </span>
        </div>
      )}

      {/* Bar */}
      <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100">
        <div
          className={cn("h-full rounded-full transition-all duration-500", barColor)}
          style={{ width: `${displayPct}%` }}
        />
      </div>

      {/* Details */}
      <div className="flex items-center justify-between text-xs text-slate-500">
        <span>
          <span className="font-mono font-medium tabular-nums text-slate-700">
            {consumed}
          </span>
          h consumed
        </span>
        <span>
          <span className="font-mono font-medium tabular-nums text-slate-700">
            {allocated}
          </span>
          h allocated
        </span>
      </div>
    </div>
  );
}
