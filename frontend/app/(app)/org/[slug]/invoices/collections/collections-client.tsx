"use client";

import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { formatCurrency, formatDateTime } from "@/lib/format";
import type { AiGateListItem } from "@/lib/api/ai";
import type { DebtorResponse } from "@/lib/api/collections";
import { ReminderQueue, type ReminderPreview } from "./reminder-queue";

interface CollectionsClientProps {
  slug: string;
  debtors: DebtorResponse[];
  /** Only populated for AI_REVIEW holders; when null the queue section is hidden. */
  gates: AiGateListItem[] | null;
  previews: Record<string, ReminderPreview>;
}

function activityBadgeVariant(status: string) {
  switch (status) {
    case "SENT":
      return "success" as const;
    case "SEND_FAILED":
    case "FLAGGED":
      return "destructive" as const;
    case "PROPOSED":
      return "warning" as const;
    default:
      return "neutral" as const;
  }
}

function formatDaysOverdue(days: number): string {
  if (days > 0) return `${days}d overdue`;
  if (days === 0) return "Due today";
  return `${Math.abs(days)}d until due`;
}

function formatActivityStatus(status: string): string {
  return status
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function CollectionsClient({ slug, debtors, gates, previews }: CollectionsClientProps) {
  return (
    <div className="space-y-10">
      {/* Debtor book */}
      <section className="space-y-4">
        <div>
          <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">Debtor book</h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Outstanding balances grouped by customer and currency, aged into buckets.
          </p>
        </div>

        {debtors.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-200 px-6 py-12 text-center dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No outstanding balances. Everything is settled.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800">
                  <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                    Customer
                  </th>
                  <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                    Outstanding
                  </th>
                  <th className="hidden px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase sm:table-cell dark:text-slate-400">
                    Oldest
                  </th>
                  <th className="hidden px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase lg:table-cell dark:text-slate-400">
                    Current
                  </th>
                  <th className="hidden px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase lg:table-cell dark:text-slate-400">
                    30d
                  </th>
                  <th className="hidden px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase lg:table-cell dark:text-slate-400">
                    60d
                  </th>
                  <th className="hidden px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase lg:table-cell dark:text-slate-400">
                    90d+
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                    Signals
                  </th>
                  <th className="hidden px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase xl:table-cell dark:text-slate-400">
                    Last activity
                  </th>
                </tr>
              </thead>
              <tbody>
                {debtors.map((debtor) => (
                  <tr
                    key={`${debtor.customerId}-${debtor.currency}`}
                    className="group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                  >
                    <td className="px-4 py-3">
                      <Link
                        href={`/org/${slug}/customers/${debtor.customerId}`}
                        className="font-medium text-slate-950 hover:underline dark:text-slate-50"
                      >
                        {debtor.customerName}
                      </Link>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {debtor.invoiceCount} invoice{debtor.invoiceCount === 1 ? "" : "s"} ·{" "}
                        {debtor.currency}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-right font-mono text-sm font-medium text-slate-900 tabular-nums dark:text-slate-100">
                      {formatCurrency(debtor.outstandingTotal, debtor.currency)}
                    </td>
                    <td className="hidden px-4 py-3 text-right font-mono text-sm text-slate-700 tabular-nums sm:table-cell dark:text-slate-300">
                      {formatDaysOverdue(debtor.oldestDaysOverdue)}
                    </td>
                    <td className="hidden px-4 py-3 text-right font-mono text-sm text-slate-700 tabular-nums lg:table-cell dark:text-slate-300">
                      {formatCurrency(debtor.buckets.current, debtor.currency)}
                    </td>
                    <td className="hidden px-4 py-3 text-right font-mono text-sm text-slate-700 tabular-nums lg:table-cell dark:text-slate-300">
                      {formatCurrency(debtor.buckets.d30, debtor.currency)}
                    </td>
                    <td className="hidden px-4 py-3 text-right font-mono text-sm text-slate-700 tabular-nums lg:table-cell dark:text-slate-300">
                      {formatCurrency(debtor.buckets.d60, debtor.currency)}
                    </td>
                    <td className="hidden px-4 py-3 text-right font-mono text-sm text-slate-700 tabular-nums lg:table-cell dark:text-slate-300">
                      {formatCurrency(debtor.buckets.d90plus, debtor.currency)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap items-center gap-1">
                        {debtor.collectionsExempt && <Badge variant="neutral">Exempt</Badge>}
                        {debtor.signals.map((signal) => (
                          <Badge key={signal} variant="warning">
                            {signal}
                          </Badge>
                        ))}
                        {!debtor.collectionsExempt && debtor.signals.length === 0 && (
                          <span className="text-xs text-slate-400 dark:text-slate-500">—</span>
                        )}
                      </div>
                    </td>
                    <td className="hidden px-4 py-3 text-left text-sm xl:table-cell">
                      {debtor.lastActivity ? (
                        <div className="flex flex-col gap-0.5">
                          <Badge variant={activityBadgeVariant(debtor.lastActivity.status)}>
                            {formatActivityStatus(debtor.lastActivity.status)}
                          </Badge>
                          <span className="font-mono text-xs text-slate-400 tabular-nums dark:text-slate-500">
                            {formatDateTime(debtor.lastActivity.at)}
                          </span>
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400 dark:text-slate-500">
                          No chase history
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Pending-reminder queue — AI_REVIEW holders only */}
      {gates !== null && (
        <section className="space-y-4">
          <div>
            <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">
              Pending reminders
            </h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Review the drafted reminders before they are sent to customers.
            </p>
          </div>
          <ReminderQueue slug={slug} gates={gates} previews={previews} />
        </section>
      )}
    </div>
  );
}
