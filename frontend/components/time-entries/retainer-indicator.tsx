"use client";

import { cn } from "@/lib/utils";
import type { RetainerSummaryResponse } from "@/lib/api/retainers";

interface RetainerIndicatorProps {
  summary: RetainerSummaryResponse | null;
  className?: string;
}

export function RetainerIndicator({
  summary,
  className,
}: RetainerIndicatorProps) {
  if (!summary || !summary.hasActiveRetainer) {
    return null;
  }

  // FIXED_FEE: show simple label
  if (summary.type === "FIXED_FEE") {
    return (
      <div
        className={cn(
          "rounded-md border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-800 dark:bg-slate-900/50",
          className,
        )}
      >
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Fixed Fee Retainer
          {summary.agreementName && (
            <span className="ml-1 font-medium text-slate-900 dark:text-slate-100">
              &mdash; {summary.agreementName}
            </span>
          )}
        </p>
      </div>
    );
  }

  // HOUR_BANK: show hours remaining with color + optional overage warning
  const percent = summary.percentConsumed ?? 0;

  let textColor = "text-green-600 dark:text-green-400";
  if (percent >= 100) {
    textColor = "text-red-600 dark:text-red-400";
  } else if (percent >= 80) {
    textColor = "text-amber-600 dark:text-amber-400";
  }

  const remainingHours = summary.remainingHours ?? 0;

  return (
    <div className={cn("space-y-2", className)}>
      <div
        className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-800 dark:bg-slate-900/50"
        data-testid="retainer-indicator"
      >
        <p className={cn("text-sm font-medium", textColor)}>
          Retainer: {remainingHours.toFixed(1)} hrs remaining
        </p>
        {summary.agreementName && (
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {summary.agreementName}
          </p>
        )}
      </div>

      {summary.isOverage && (
        <div
          role="alert"
          className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 dark:border-amber-800/50 dark:bg-amber-900/20"
          data-testid="retainer-overage-warning"
        >
          <p className="text-sm text-amber-700 dark:text-amber-400">
            Retainer fully consumed &mdash; this time will be billed as overage.
          </p>
        </div>
      )}
    </div>
  );
}
