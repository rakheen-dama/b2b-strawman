"use client";

import { cn } from "@/lib/utils";
import type { RetainerSummaryResponse } from "@/lib/types";

function formatPeriodDate(dateStr: string): string {
  const [year, month, day] = dateStr.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return date.toLocaleDateString("en-ZA", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/** Returns true if the given YYYY-MM-DD date falls outside the period [start, end). */
function isOutsidePeriod(
  date: string,
  periodStart: string | null,
  periodEnd: string | null,
): boolean {
  if (!periodStart || !periodEnd || !date) return false;
  return date < periodStart || date >= periodEnd;
}

interface RetainerIndicatorProps {
  summary: RetainerSummaryResponse | null;
  /** The currently selected date in YYYY-MM-DD format (from the time entry form). */
  selectedDate?: string;
  className?: string;
}

export function RetainerIndicator({
  summary,
  selectedDate,
  className,
}: RetainerIndicatorProps) {
  if (!summary || !summary.hasActiveRetainer) {
    return null;
  }

  const hasPeriodDates = !!summary.periodStart && !!summary.periodEnd;
  const outsidePeriod =
    !!selectedDate &&
    isOutsidePeriod(selectedDate, summary.periodStart, summary.periodEnd);

  // FIXED_FEE: show simple label
  if (summary.type === "FIXED_FEE") {
    return (
      <div className={cn("space-y-2", className)}>
        <div
          aria-label={`Fixed fee retainer${summary.agreementName ? `: ${summary.agreementName}` : ""}`}
          className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-800 dark:bg-slate-900/50"
        >
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Fixed Fee Retainer
            {summary.agreementName && (
              <span className="ml-1 font-medium text-slate-900 dark:text-slate-100">
                &mdash; {summary.agreementName}
              </span>
            )}
          </p>
          {hasPeriodDates && (
            <p
              className="mt-0.5 text-xs text-slate-500 dark:text-slate-400"
              data-testid="retainer-period-range"
            >
              Period: {formatPeriodDate(summary.periodStart!)} &ndash;{" "}
              {formatPeriodDate(summary.periodEnd!)}
            </p>
          )}
        </div>

        {outsidePeriod && (
          <div
            role="alert"
            className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 dark:border-amber-800/50 dark:bg-amber-900/20"
            data-testid="retainer-period-warning"
          >
            <p className="text-sm text-amber-700 dark:text-amber-400">
              This date is outside the current retainer period &mdash; hours
              won&apos;t count toward the retainer.
            </p>
          </div>
        )}
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
    <div
      className={cn("space-y-2", className)}
      aria-label={`Hour bank retainer: ${remainingHours.toFixed(1)} hours remaining${summary.isOverage ? ", overage active" : ""}`}
    >
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
        {hasPeriodDates && (
          <p
            className="mt-0.5 text-xs text-slate-500 dark:text-slate-400"
            data-testid="retainer-period-range"
          >
            Period: {formatPeriodDate(summary.periodStart!)} &ndash;{" "}
            {formatPeriodDate(summary.periodEnd!)}
          </p>
        )}
      </div>

      {outsidePeriod && (
        <div
          role="alert"
          className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 dark:border-amber-800/50 dark:bg-amber-900/20"
          data-testid="retainer-period-warning"
        >
          <p className="text-sm text-amber-700 dark:text-amber-400">
            This date is outside the current retainer period &mdash; hours
            won&apos;t count toward the retainer.
          </p>
        </div>
      )}

      {!outsidePeriod && summary.isOverage && (
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
