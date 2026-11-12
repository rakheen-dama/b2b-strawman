import Link from "next/link";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { StackedMeter } from "@/components/charts/stacked-meter";
import { CHART_THEME } from "@/lib/chart-theme";
import { formatCurrency } from "@/lib/format";
import type { DebtorBuckets, PaginatedDebtorsResponse } from "@/lib/api/collections";
import {
  WIDGET_LINK_CLASS,
  WidgetErrorLine,
  WidgetHeadline,
} from "@/components/dashboard/widget-parts";

/**
 * Aging buckets, current → 90+ days. Ordered, so they take the one-hue ordinal `--aging-*` ramp
 * (ADR-370) — not the categorical slots, not status colours ("current" is not "good"); the meter's
 * `palette="ordinal-aging"` draws them in this order. Never colour alone: each row's meter has an
 * accessible name and a visually hidden breakdown of every bucket, and the card a text legend.
 */
// Legend dots read `color`; StackedMeter colours by index — keep in step with ordinal-aging.
const BUCKETS: ReadonlyArray<{ key: keyof DebtorBuckets; label: string; color: string }> = [
  { key: "current", label: "Current", color: CHART_THEME.aging.current },
  { key: "d30", label: "30 days", color: CHART_THEME.aging.d30 },
  { key: "d60", label: "60 days", color: CHART_THEME.aging.d60 },
  { key: "d90plus", label: "90+ days", color: CHART_THEME.aging.d90plus },
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
                  <StackedMeter
                    palette="ordinal-aging"
                    height={6}
                    label={`Aging of ${debtor.clientName}'s outstanding fees`}
                    segments={BUCKETS.map((b) => ({
                      key: b.key,
                      label: b.label,
                      value: debtor.buckets[b.key],
                      valueText: formatCurrency(debtor.buckets[b.key], debtor.currency),
                    }))}
                  />
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
