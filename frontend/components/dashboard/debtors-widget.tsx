import Link from "next/link";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { MicroStackedBar } from "@/components/dashboard/micro-stacked-bar";
import { formatCurrency } from "@/lib/format";
import type { DebtorBuckets, PaginatedDebtorsResponse } from "@/lib/api/collections";
import {
  WIDGET_LINK_CLASS,
  WidgetErrorLine,
  WidgetHeadline,
} from "@/components/dashboard/widget-parts";

/**
 * Aging buckets, oldest warmest. Existing palette only (no new tokens), and never colour alone:
 * each segment has a title, each row a visually hidden breakdown, and the card a text legend.
 */
const BUCKETS: ReadonlyArray<{ key: keyof DebtorBuckets; label: string; color: string }> = [
  { key: "current", label: "Current", color: "var(--color-teal-500, #14b8a6)" },
  { key: "d30", label: "30 days", color: "var(--color-slate-300, #cbd5e1)" },
  { key: "d60", label: "60 days", color: "var(--color-amber-400, #fbbf24)" },
  { key: "d90plus", label: "90+ days", color: "var(--color-amber-600, #d97706)" },
];

interface DebtorsWidgetProps {
  slug: string;
  /** `null` = the read failed. Top 5 debtors by outstanding total. */
  data: PaginatedDebtorsResponse | null;
}

function overdueText(days: number): string {
  if (days <= 0) return "Not yet overdue";
  return `${days} ${days === 1 ? "day" : "days"} overdue`;
}

/**
 * Debtors (614B, arch §12.6.3): clients owing fees, largest first, each with its aging split.
 * The headline is the number of debtors — five rows cannot honestly sum to a firm total.
 */
export function DebtorsWidget({ slug, data }: DebtorsWidgetProps) {
  const count = data?.page.totalElements ?? 0;
  return (
    <Card data-testid="debtors-widget" className="gap-4">
      <CardHeader>
        <CardTitle className="text-sm font-medium">Debtors</CardTitle>
        <CardAction>
          <Link href={`/org/${slug}/invoices/collections`} className={WIDGET_LINK_CLASS}>
            Open collections
          </Link>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        {data === null ? (
          <WidgetErrorLine subject="Debtors" />
        ) : count === 0 ? (
          <p className="text-sm text-slate-400">No client owes fees.</p>
        ) : (
          <>
            <div>
              <WidgetHeadline testId="debtors-headline">{count}</WidgetHeadline>
              <p className="text-xs text-slate-500">
                {count === 1 ? "client owes fees" : "clients owe fees"}
              </p>
            </div>
            <ul className="space-y-1" aria-label="Largest debtors">
              {data.content.map((debtor) => (
                <li
                  key={`${debtor.clientId}:${debtor.currency}`}
                  className="space-y-1 rounded-md px-2 py-1.5"
                >
                  <div className="flex items-baseline gap-2 text-sm">
                    <span className="min-w-0 flex-1">
                      <span className="block truncate">{debtor.clientName}</span>
                      <span className="block text-[11px] text-slate-500">
                        {overdueText(debtor.oldestDaysOverdue)}
                      </span>
                    </span>
                    <span className="font-mono text-sm font-medium text-slate-900 tabular-nums dark:text-slate-100">
                      {formatCurrency(debtor.outstandingTotal, debtor.currency)}
                    </span>
                  </div>
                  <MicroStackedBar
                    height={6}
                    segments={BUCKETS.map((b) => ({
                      value: debtor.buckets[b.key],
                      color: b.color,
                      label: b.label,
                    }))}
                  />
                  <span className="sr-only">
                    {BUCKETS.map(
                      (b) => `${b.label} ${formatCurrency(debtor.buckets[b.key], debtor.currency)}`
                    ).join(", ")}
                  </span>
                </li>
              ))}
            </ul>
            <ul
              aria-label="Aging legend"
              className="flex flex-wrap gap-x-3 gap-y-1 px-2 text-[11px] text-slate-500"
            >
              {BUCKETS.map((b) => (
                <li key={b.key} className="flex items-center gap-1">
                  <span
                    aria-hidden="true"
                    className="inline-block size-2 rounded-full"
                    style={{ backgroundColor: b.color }}
                  />
                  {b.label}
                </li>
              ))}
            </ul>
          </>
        )}
      </CardContent>
    </Card>
  );
}
