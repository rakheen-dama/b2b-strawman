import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { TrustPositionResponse } from "@/lib/dashboard-types";
import {
  WIDGET_LINK_CLASS,
  WidgetErrorLine,
  WidgetHeadline,
} from "@/components/dashboard/widget-parts";

interface TrustPositionWidgetProps {
  slug: string;
  /** `null` = the read failed. (No account / module off / no VIEW_TRUST = widget absent.) */
  data: TrustPositionResponse | null;
}

/** Half a cent either way is a rounding artefact, not a difference. */
function isBalanced(difference: number): boolean {
  return Math.abs(difference) < 0.005;
}

function lastReconciliationText(data: TrustPositionResponse): string {
  if (!data.lastReconciliationPeriodEnd) return "Never reconciled";
  const period = `Period to ${formatLocalDate(data.lastReconciliationPeriodEnd)}`;
  if (data.lastReconciliationStatus === "DRAFT") return `${period}, in draft`;
  return data.lastReconciliationBalanced === false ? `${period}, not balanced` : period;
}

/**
 * Trust position (614B, arch §12.3.3): the headline is the difference between the bank cashbook
 * and the client ledgers — "does the bank agree with the ledgers". Primary account only; no
 * switcher yet (follow-up), so more than one account is noted rather than hidden.
 */
export function TrustPositionWidget({ slug, data }: TrustPositionWidgetProps) {
  const balanced = data ? isBalanced(data.difference) : false;
  return (
    <Card data-testid="trust-position-widget" className="gap-4">
      <CardHeader>
        <CardTitle className="text-sm font-medium">Trust position</CardTitle>
        <CardAction>
          <Link href={`/org/${slug}/trust-accounting/reconciliation`} className={WIDGET_LINK_CLASS}>
            Open reconciliation
          </Link>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        {data === null ? (
          <WidgetErrorLine subject="The trust position" />
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-2">
              <WidgetHeadline testId="trust-position-headline">
                {formatCurrency(balanced ? 0 : data.difference)}
              </WidgetHeadline>
              {balanced ? (
                <Badge variant="success">Balanced</Badge>
              ) : (
                <Badge variant="warning">Difference {formatCurrency(data.difference)}</Badge>
              )}
            </div>
            <p className="truncate text-xs text-slate-500">
              {data.accountName}
              {data.bankName && <span className="text-slate-400">, {data.bankName}</span>}
            </p>
            <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1.5 text-xs">
              <dt className="text-slate-500">Bank cashbook</dt>
              <dd className="text-right font-mono text-sm font-medium text-slate-900 tabular-nums dark:text-slate-100">
                {formatCurrency(data.cashbookBalance)}
              </dd>
              <dt className="text-slate-500">Client ledgers</dt>
              <dd className="text-right font-mono text-sm font-medium text-slate-900 tabular-nums dark:text-slate-100">
                {formatCurrency(data.clientLedgerTotal)}
              </dd>
              <dt className="text-slate-500">Last reconciliation</dt>
              <dd className="text-right text-slate-700 dark:text-slate-300">
                {lastReconciliationText(data)}
              </dd>
              <dt className="text-slate-500">Awaiting approval</dt>
              <dd className="text-right font-mono text-slate-700 tabular-nums dark:text-slate-300">
                {data.pendingApprovalCount}
              </dd>
            </dl>
            {data.accounts.length > 1 && (
              <p className="text-xs text-slate-400">
                Primary of {data.accounts.length} trust accounts
              </p>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
