import Link from "next/link";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { formatCurrency, formatDuration, formatLocalDate } from "@/lib/format";
import type { UnbilledWipResponse } from "@/lib/dashboard-types";
import {
  WIDGET_LINK_CLASS,
  WidgetErrorLine,
  WidgetHeadline,
} from "@/components/dashboard/widget-parts";

/** Fee earners listed before the card defers to the Unbilled work page. */
const MAX_ROWS = 5;

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
 * Unbilled WIP (614B, arch §12.6.3). Full view: firm value headline + value and hours per fee
 * earner. Redacted view (`redacted: true`, callers without FINANCIAL_VISIBILITY): "My unbilled
 * time" — the caller's own hours only; the response carries no values, and none are rendered.
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
  return (
    <Card data-testid="unbilled-wip-widget" data-variant="personal" className="gap-4">
      <CardHeader>
        <CardTitle className="text-sm font-medium">My unbilled time</CardTitle>
        <CardAction>
          <Link href={`/org/${slug}/my-work/timesheet`} className={WIDGET_LINK_CLASS}>
            Open timesheet
          </Link>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-1">
        {data === null ? (
          <WidgetErrorLine subject="Your unbilled time" />
        ) : data.totalMinutes === 0 ? (
          <p className="text-sm text-slate-400">
            No unbilled time. Billable time you record stays here until it is on a fee note.
          </p>
        ) : (
          <>
            <WidgetHeadline testId="unbilled-wip-headline">
              {formatDuration(data.totalMinutes)}
            </WidgetHeadline>
            <p className="text-xs text-slate-500">
              Billable time not yet on a fee note
              {own?.oldestEntryDate && <>, oldest from {formatLocalDate(own.oldestEntryDate)}</>}
            </p>
          </>
        )}
      </CardContent>
    </Card>
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
  const rows = data?.byFeeEarner.slice(0, MAX_ROWS) ?? [];
  const moreCount = (data?.byFeeEarner.length ?? 0) - rows.length;
  return (
    <Card data-testid="unbilled-wip-widget" data-variant="firm" className="gap-4">
      <CardHeader>
        <CardTitle className="text-sm font-medium">Unbilled WIP</CardTitle>
        {showLink && (
          <CardAction>
            <Link href={`/org/${slug}/invoices/unbilled`} className={WIDGET_LINK_CLASS}>
              Open unbilled work
            </Link>
          </CardAction>
        )}
      </CardHeader>
      <CardContent className="space-y-3">
        {data === null ? (
          <WidgetErrorLine subject="Unbilled WIP" />
        ) : data.byFeeEarner.length === 0 && !data.unbilledDisbursementTotal ? (
          <p className="text-sm text-slate-400">
            Nothing unbilled. Billable time appears here until it is on a fee note.
          </p>
        ) : (
          <>
            <div>
              <WidgetHeadline testId="unbilled-wip-headline">
                {data.totalValue != null ? formatCurrency(data.totalValue, data.currency) : "–"}
              </WidgetHeadline>
              <p className="text-xs text-slate-500">
                {formatDuration(data.totalMinutes)} of unbilled time
              </p>
            </div>
            {rows.length > 0 && (
              <ul className="space-y-0.5" aria-label="Unbilled WIP by fee earner">
                {rows.map((row) => (
                  <li
                    key={row.memberId}
                    className="flex items-baseline gap-2 rounded-md px-2 py-1.5 text-sm"
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate">{row.memberName}</span>
                      <span className="block text-[11px] text-slate-500">
                        Oldest from {formatLocalDate(row.oldestEntryDate)}
                      </span>
                    </span>
                    <span className="font-mono text-xs text-slate-600 tabular-nums dark:text-slate-400">
                      {formatDuration(row.minutes)}
                    </span>
                    <span className="w-28 text-right font-mono text-sm font-medium text-slate-900 tabular-nums dark:text-slate-100">
                      {row.value != null ? formatCurrency(row.value, data.currency) : "–"}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            {moreCount > 0 && (
              <p className="px-2 text-xs text-slate-500">
                {moreCount} more fee {moreCount === 1 ? "earner" : "earners"}
              </p>
            )}
            {data.unbilledDisbursementTotal != null && (
              <p
                data-testid="unbilled-disbursements"
                className="flex items-baseline justify-between border-t border-slate-100 px-2 pt-2 text-xs text-slate-500 dark:border-slate-800"
              >
                <span>Disbursements not yet billed</span>
                <span className="font-mono text-sm font-medium text-slate-900 tabular-nums dark:text-slate-100">
                  {formatCurrency(data.unbilledDisbursementTotal, data.currency)}
                </span>
              </p>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
