"use client";

import { Badge } from "@/components/ui/badge";
import { formatDate, formatCurrency } from "@/lib/format";
import type { PaymentEvent, PaymentEventStatus } from "@/lib/types";

const STATUS_BADGE: Record<
  PaymentEventStatus,
  {
    label: string;
    variant: "success" | "warning" | "destructive" | "neutral" | "lead";
  }
> = {
  CREATED: { label: "Created", variant: "neutral" },
  PENDING: { label: "Pending", variant: "lead" },
  COMPLETED: { label: "Completed", variant: "success" },
  FAILED: { label: "Failed", variant: "destructive" },
  EXPIRED: { label: "Expired", variant: "warning" },
  CANCELLED: { label: "Cancelled", variant: "neutral" },
};

const PROVIDER_LABELS: Record<string, string> = {
  stripe: "Stripe",
  payfast: "PayFast",
  manual: "Manual",
  noop: "Manual",
};

interface PaymentEventHistoryProps {
  events: PaymentEvent[];
}

export function PaymentEventHistory({ events }: PaymentEventHistoryProps) {
  if (!events || events.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
        <h3 className="mb-2 font-semibold text-slate-900 dark:text-slate-100">
          Payment History
        </h3>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No payment events yet.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
      <h3 className="mb-3 font-semibold text-slate-900 dark:text-slate-100">
        Payment History
      </h3>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Status
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Provider
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Reference
              </th>
              <th className="px-3 py-2 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Amount
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Date
              </th>
            </tr>
          </thead>
          <tbody>
            {events.map((event) => {
              const badge = STATUS_BADGE[event.status] ?? STATUS_BADGE.CREATED;
              return (
                <tr
                  key={event.id}
                  className="border-b border-slate-100 last:border-0 dark:border-slate-800/50"
                >
                  <td className="px-3 py-2">
                    <Badge variant={badge.variant}>{badge.label}</Badge>
                  </td>
                  <td className="px-3 py-2 text-sm text-slate-600 dark:text-slate-400">
                    {PROVIDER_LABELS[event.providerSlug] ??
                      event.providerSlug}
                  </td>
                  <td className="px-3 py-2 font-mono text-sm text-slate-600 dark:text-slate-400">
                    {event.paymentReference ?? "\u2014"}
                  </td>
                  <td className="px-3 py-2 text-right text-sm text-slate-900 dark:text-slate-100">
                    {formatCurrency(event.amount, event.currency)}
                  </td>
                  <td className="px-3 py-2 text-sm text-slate-600 dark:text-slate-400">
                    {formatDate(event.createdAt)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
