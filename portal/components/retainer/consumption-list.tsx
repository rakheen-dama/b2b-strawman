"use client";

import { useEffect, useMemo, useState } from "react";
import { ClipboardList } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate } from "@/lib/format";
import {
  formatHours,
  getConsumption,
  previousPeriodBounds,
  type PortalRetainerConsumptionEntry,
  type PortalRetainerPeriodType,
} from "@/lib/api/retainer";

type PeriodKey = "current" | "previous" | "custom";

interface ConsumptionListProps {
  retainerId: string;
  /** Current period start (ISO date, "YYYY-MM-DD") — used as default `from`. */
  periodStart: string;
  /** Current period end (ISO date, "YYYY-MM-DD") — used as default `to`. */
  periodEnd: string;
  /** Period type — controls how "previous period" bounds are computed. */
  periodType: PortalRetainerPeriodType;
}

interface DateGroup {
  date: string;
  entries: PortalRetainerConsumptionEntry[];
}

/**
 * Groups consumption entries by `occurredAt` (ISO date). Preserves the
 * input order within each group — the backend already returns entries
 * sorted descending by date, so groups come out newest-first.
 */
function groupByDate(
  entries: PortalRetainerConsumptionEntry[],
): DateGroup[] {
  const byDate = new Map<string, PortalRetainerConsumptionEntry[]>();
  for (const entry of entries) {
    const bucket = byDate.get(entry.occurredAt) ?? [];
    bucket.push(entry);
    byDate.set(entry.occurredAt, bucket);
  }
  return Array.from(byDate.entries()).map(([date, bucketEntries]) => ({
    date,
    entries: bucketEntries,
  }));
}

/**
 * Renders consumption entries for a retainer, grouped by date. Includes a
 * period selector that re-fetches from the backend on change. Entries are
 * sanitised server-side (ADR-254) so descriptions can be rendered as plain
 * text without additional escaping.
 */
export function ConsumptionList({
  retainerId,
  periodStart,
  periodEnd,
  periodType,
}: ConsumptionListProps) {
  const [period, setPeriod] = useState<PeriodKey>("current");
  const [customFrom, setCustomFrom] = useState<string>(periodStart);
  const [customTo, setCustomTo] = useState<string>(periodEnd);
  const [entries, setEntries] = useState<
    PortalRetainerConsumptionEntry[] | null
  >(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const range = useMemo(() => {
    if (period === "current") {
      return { from: periodStart, to: periodEnd };
    }
    if (period === "previous") {
      return previousPeriodBounds(periodStart, periodType);
    }
    return { from: customFrom || undefined, to: customTo || undefined };
  }, [period, customFrom, customTo, periodStart, periodEnd, periodType]);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    (async () => {
      try {
        const data = await getConsumption(retainerId, range);
        if (cancelled) return;
        setEntries(data);
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : "Failed to load retainer consumption",
          );
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [retainerId, range]);

  const groups = entries ? groupByDate(entries) : [];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <label
          htmlFor="retainer-period"
          className="text-sm font-medium text-slate-700"
        >
          Period
        </label>
        <select
          id="retainer-period"
          value={period}
          onChange={(e) => setPeriod(e.target.value as PeriodKey)}
          className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
        >
          <option value="current">Current period</option>
          <option value="previous">Previous period</option>
          <option value="custom">Custom range</option>
        </select>
        {period === "custom" && (
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="date"
              aria-label="Custom from date"
              value={customFrom}
              onChange={(e) => setCustomFrom(e.target.value)}
              className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
            />
            <span className="text-sm text-slate-500">to</span>
            <input
              type="date"
              aria-label="Custom to date"
              value={customTo}
              onChange={(e) => setCustomTo(e.target.value)}
              className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
            />
          </div>
        )}
      </div>

      {isLoading && (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      )}

      {!isLoading && error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {!isLoading && !error && entries !== null && entries.length === 0 && (
        <div
          className="flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white py-12 text-center"
          data-testid="consumption-empty"
        >
          <ClipboardList
            className="mb-3 size-10 text-slate-300"
            aria-hidden="true"
          />
          <p className="text-sm font-medium text-slate-600">
            No consumption recorded for this period
          </p>
          <p className="mt-1 text-xs text-slate-500">
            Time logged against this retainer will appear here.
          </p>
        </div>
      )}

      {!isLoading && !error && groups.length > 0 && (
        <div className="space-y-6" aria-label="Retainer consumption entries">
          {groups.map((group) => (
            <section
              key={group.date}
              aria-label={`Entries on ${formatDate(group.date)}`}
              data-testid={`consumption-group-${group.date}`}
            >
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                {formatDate(group.date)}
              </h3>
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
                {group.entries.map((entry) => (
                  <li
                    key={entry.id}
                    className="flex items-start justify-between gap-3 px-4 py-3"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium text-slate-900">
                        {entry.description || "Time entry"}
                      </p>
                      <p className="mt-0.5 text-xs text-slate-500">
                        {entry.memberDisplayName}
                      </p>
                    </div>
                    <p className="font-mono text-sm font-semibold text-slate-900 tabular-nums">
                      {formatHours(entry.hours)}
                    </p>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
