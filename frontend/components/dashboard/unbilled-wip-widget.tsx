import type { ReactNode } from "react";
import Link from "next/link";
import { Hourglass } from "lucide-react";
import { StatCard } from "@b2mash/ui/stat-card";
import { EmptyState } from "@/components/empty-state";
import { UnbilledWipChart } from "@/components/dashboard/unbilled-wip-chart";
import { formatCurrency, formatDuration, formatLocalDate } from "@/lib/format";
import type { UnbilledWipResponse } from "@/lib/dashboard-types";
import {
  WIDGET_ELEVATION_CLASS,
  WIDGET_LINK_CLASS,
  WidgetCard,
  WidgetErrorLine,
  WidgetHeadline,
} from "@/components/dashboard/widget-parts";

const FIRM_TITLE = "Unbilled WIP";

interface UnbilledWipWidgetProps {
  slug: string;
  /** `null` = the read failed. */
  data: UnbilledWipResponse | null;
  /** Fee-earner Home: the read comes back redacted, so the card is "My unbilled time". */
  personal: boolean;
  /** "Open unbilled work" — the page needs INVOICING, so the link is gated separately. */
  showLink: boolean;
}

/**
 * Unbilled WIP (614B, arch §12.6.3; on the kit in 640B). Full view: firm value headline + a
 * ranked bar of value per fee earner (≥ 2 earners — one bar is not a chart, §15.9; hours and the
 * oldest date per earner live on Unbilled work). Redacted view (`redacted: true`, callers without
 * FINANCIAL_VISIBILITY): "My unbilled time" — a StatCard of the caller's own hours only; the
 * response carries no values, and none are rendered.
 */
export function UnbilledWipWidget({ slug, data, personal, showLink }: UnbilledWipWidgetProps) {
  const redacted = data ? data.redacted : personal;
  return redacted ? (
    <MyUnbilledTime slug={slug} data={data} />
  ) : (
    <FirmUnbilledWip slug={slug} data={data} showLink={showLink} />
  );
}

function MyUnbilledTime({ slug, data }: { slug: string; data: UnbilledWipResponse | null }) {
  const own = data?.byFeeEarner[0] ?? null;
  let value: ReactNode = null;
  let hint: ReactNode;
  if (data === null) {
    hint = <WidgetErrorLine subject="Your unbilled time" />;
  } else if (data.totalMinutes === 0) {
    value = formatDuration(0);
    hint = "No unbilled time. Billable time you record stays here until it is on a fee note.";
  } else {
    value = <span data-testid="unbilled-wip-headline">{formatDuration(data.totalMinutes)}</span>;
    hint = (
      <>
        Billable time not yet on a fee note
        {own?.oldestEntryDate && <>, oldest from {formatLocalDate(own.oldestEntryDate)}</>}
      </>
    );
  }
  return (
    // The wrapper keeps the widget's testid + variant; the StatCard is the only frame.
    <div
      data-testid="unbilled-wip-widget"
      data-variant="personal"
      className="flex flex-col *:flex-1"
    >
      <StatCard
        label="My unbilled time"
        value={value}
        hint={hint}
        footer={
          <Link href={`/org/${slug}/my-work/timesheet`} className={WIDGET_LINK_CLASS}>
            Open timesheet
          </Link>
        }
        className={WIDGET_ELEVATION_CLASS}
      />
    </div>
  );
}

function FirmUnbilledWip({
  slug,
  data,
  showLink,
}: {
  slug: string;
  data: UnbilledWipResponse | null;
  showLink: boolean;
}) {
  const earners = data?.byFeeEarner ?? [];
  const single = earners.length === 1 ? earners[0] : null;
  return (
    <WidgetCard
      data-testid="unbilled-wip-widget"
      data-variant="firm"
      title={FIRM_TITLE}
      action={
        showLink ? (
          <Link href={`/org/${slug}/invoices/unbilled`} className={WIDGET_LINK_CLASS}>
            Open unbilled work
          </Link>
        ) : null
      }
      contentProps={{ className: "space-y-3" }}
    >
      {data === null ? (
        <WidgetErrorLine subject="Unbilled WIP" />
      ) : earners.length === 0 && !data.unbilledDisbursementTotal ? (
        <EmptyState
          variant="compact"
          icon={Hourglass}
          title="Nothing unbilled."
          description="Billable time appears here until it is on a fee note."
        />
      ) : (
        <>
          <div>
            <WidgetHeadline testId="unbilled-wip-headline">
              {data.totalValue != null ? formatCurrency(data.totalValue, data.currency) : "–"}
            </WidgetHeadline>
            <p className="text-muted-foreground text-xs">
              {formatDuration(data.totalMinutes)} of unbilled time
            </p>
          </div>
          {earners.length >= 2 ? (
            <UnbilledWipChart
              title={FIRM_TITLE}
              rows={earners.map((row) => ({ feeEarner: row.memberName, value: row.value }))}
              currency={data.currency}
            />
          ) : single ? (
            <ul aria-label="Unbilled WIP by fee earner">
              <li className="flex items-baseline gap-2 rounded-md px-2 py-1.5 text-sm">
                <span className="text-foreground min-w-0 flex-1 truncate">{single.memberName}</span>
                <span className="text-foreground font-mono text-sm font-medium tabular-nums">
                  {single.value != null ? formatCurrency(single.value, data.currency) : "–"}
                </span>
              </li>
            </ul>
          ) : null}
          {data.unbilledDisbursementTotal != null && (
            <p
              data-testid="unbilled-disbursements"
              className="border-border text-muted-foreground flex items-baseline justify-between border-t px-2 pt-2 text-xs"
            >
              <span>Disbursements not yet billed</span>
              <span className="text-foreground font-mono text-sm font-medium tabular-nums">
                {formatCurrency(data.unbilledDisbursementTotal, data.currency)}
              </span>
            </p>
          )}
        </>
      )}
    </WidgetCard>
  );
}
