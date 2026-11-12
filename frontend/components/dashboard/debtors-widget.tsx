import Link from "next/link";
import { Wallet } from "lucide-react";
import { StackedMeter } from "@/components/charts/stacked-meter";
import { EmptyState } from "@/components/empty-state";
import { formatCurrency } from "@/lib/format";
import type { DebtorBuckets, PaginatedDebtorsResponse } from "@/lib/api/collections";
import {
  WIDGET_LINK_CLASS,
  WidgetCard,
  WidgetErrorLine,
  WidgetHeadline,
} from "@/components/dashboard/widget-parts";

/**
 * Aging buckets, current → 90+ days. Ordered, so they take the one-hue ordinal `--aging-*` ramp
 * (ADR-370) — not the categorical slots, not status colours ("current" is not "good"); the meter's
 * `palette="ordinal-aging"` draws them in this order. Never colour alone: each row's meter has an
 * accessible name and a visually hidden breakdown of every bucket, and the card a text legend.
 */
// Legend dots use the --aging-* token utilities; StackedMeter colours by index — keep this order
// in step with ordinal-aging (current → 90+).
const BUCKETS: ReadonlyArray<{ key: keyof DebtorBuckets; label: string; dotClass: string }> = [
  { key: "current", label: "Current", dotClass: "bg-aging-current" },
  { key: "d30", label: "30 days", dotClass: "bg-aging-d30" },
  { key: "d60", label: "60 days", dotClass: "bg-aging-d60" },
  { key: "d90plus", label: "90+ days", dotClass: "bg-aging-d90plus" },
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
    <WidgetCard
      data-testid="debtors-widget"
      title="Debtors"
      action={
        <Link href={`/org/${slug}/invoices/collections`} className={WIDGET_LINK_CLASS}>
          Open collections
        </Link>
      }
      contentProps={{ className: "space-y-3" }}
    >
      {data === null ? (
        <WidgetErrorLine subject="Debtors" />
      ) : count === 0 ? (
        <EmptyState
          variant="compact"
          icon={Wallet}
          title="No client owes fees."
          description={null}
        />
      ) : (
        <>
          <div>
            <WidgetHeadline testId="debtors-headline">{count}</WidgetHeadline>
            <p className="text-muted-foreground text-xs">
              {count === 1 ? "client owes fees" : "clients owe fees"}
            </p>
          </div>
          {/* Two lines per debtor: name + amount, then the overdue age; the meter spans the row
              under them, so every debtor's split is drawn at the same width (comparable). */}
          <ul className="divide-border divide-y" aria-label="Largest debtors">
            {data.content.map((debtor) => (
              <li
                key={`${debtor.clientId}:${debtor.currency}`}
                className="grid grid-cols-[minmax(0,1fr)_auto] items-baseline gap-x-3 gap-y-1 px-2 py-2"
              >
                <span className="text-foreground truncate text-sm">{debtor.clientName}</span>
                <span className="text-foreground text-right font-mono text-sm font-medium tabular-nums">
                  {formatCurrency(debtor.outstandingTotal, debtor.currency)}
                </span>
                <span className="text-muted-foreground col-span-2 text-[11px]">
                  {overdueText(debtor.oldestDaysOverdue)}
                </span>
                <StackedMeter
                  className="col-span-2 mt-0.5"
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
            className="text-muted-foreground flex flex-wrap gap-x-3 gap-y-1 px-2 text-[11px]"
          >
            {BUCKETS.map((b) => (
              <li key={b.key} className="flex items-center gap-1">
                <span
                  aria-hidden="true"
                  className={`inline-block size-2 rounded-full ${b.dotClass}`}
                />
                {b.label}
              </li>
            ))}
          </ul>
        </>
      )}
    </WidgetCard>
  );
}
