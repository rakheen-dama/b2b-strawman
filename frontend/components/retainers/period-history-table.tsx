"use client";

import { useState } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatLocalDate } from "@/lib/format";
import type { PeriodSummary } from "@/lib/api/retainers";

interface PeriodHistoryTableProps {
  periods: PeriodSummary[];
  slug: string;
}

const PAGE_SIZE = 10;

export function PeriodHistoryTable({ periods, slug }: PeriodHistoryTableProps) {
  const [page, setPage] = useState(0);

  // Sort newest-first by periodStart
  const sorted = [...periods].sort(
    (a, b) => b.periodStart.localeCompare(a.periodStart),
  );

  const totalPages = Math.ceil(sorted.length / PAGE_SIZE);
  const pageItems = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  if (sorted.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
        No period history
      </p>
    );
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
        Period History
      </h3>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Period Dates
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Status
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Allocated Hours
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Consumed Hours
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Overage Hours
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Rollover Out
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Invoice
              </th>
            </tr>
          </thead>
          <tbody>
            {pageItems.map((period) => (
              <tr
                key={period.id}
                className="group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
              >
                <td className="px-4 py-3 text-sm text-slate-900 dark:text-slate-100">
                  {formatLocalDate(period.periodStart)} &ndash;{" "}
                  {formatLocalDate(period.periodEnd)}
                </td>
                <td className="px-4 py-3">
                  {period.status === "OPEN" ? (
                    <Badge variant="lead">Open</Badge>
                  ) : (
                    <Badge variant="neutral">Closed</Badge>
                  )}
                </td>
                <td className="px-4 py-3 text-right font-mono text-sm tabular-nums text-slate-700 dark:text-slate-300">
                  {period.allocatedHours != null
                    ? `${period.allocatedHours.toFixed(1)}h`
                    : "\u2014"}
                </td>
                <td className="px-4 py-3 text-right font-mono text-sm tabular-nums text-slate-700 dark:text-slate-300">
                  {period.consumedHours.toFixed(1)}h
                </td>
                <td className="px-4 py-3 text-right font-mono text-sm tabular-nums">
                  {(period.overageHours ?? 0) > 0 ? (
                    <span className="text-amber-600 dark:text-amber-400">
                      {(period.overageHours ?? 0).toFixed(1)}h
                    </span>
                  ) : (
                    <span className="text-slate-400 dark:text-slate-600">
                      &mdash;
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-right font-mono text-sm tabular-nums text-slate-700 dark:text-slate-300">
                  {period.rolloverHoursOut > 0
                    ? `${period.rolloverHoursOut.toFixed(1)}h`
                    : "\u2014"}
                </td>
                <td className="px-4 py-3 text-sm">
                  {period.invoiceId ? (
                    <Link
                      href={`/org/${slug}/invoices/${period.invoiceId}`}
                      className="text-teal-600 hover:underline dark:text-teal-400"
                    >
                      View Invoice
                    </Link>
                  ) : period.status === "CLOSED" ? (
                    <span className="text-slate-400 dark:text-slate-600">
                      &mdash;
                    </span>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between pt-2">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
            Previous
          </Button>
          <span className="text-xs text-slate-500 dark:text-slate-400">
            Page {page + 1} of {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
