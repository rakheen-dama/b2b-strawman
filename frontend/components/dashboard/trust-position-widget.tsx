import Link from "next/link";
import { AlertTriangle, CheckCircle2 } from "lucide-react";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { draftReconciliationText } from "@/lib/trust-reconciliation-copy";
import type { TrustPositionResponse } from "@/lib/dashboard-types";
import {
  StatusBadge,
  WIDGET_LINK_CLASS,
  WidgetCard,
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
  if (data.lastReconciliationStatus === "DRAFT") {
    return draftReconciliationText(data.lastReconciliationPeriodEnd);
  }
  const period = `Period to ${formatLocalDate(data.lastReconciliationPeriodEnd)}`;
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
    <WidgetCard
      data-testid="trust-position-widget"
      title="Trust position"
      action={
        <Link href={`/org/${slug}/trust-accounting/reconciliation`} className={WIDGET_LINK_CLASS}>
          Open reconciliation
        </Link>
      }
      contentProps={{ className: "space-y-3" }}
    >
      {data === null ? (
        <WidgetErrorLine subject="The trust position" />
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <WidgetHeadline testId="trust-position-headline">
              {formatCurrency(balanced ? 0 : data.difference)}
            </WidgetHeadline>
            {balanced ? (
              <StatusBadge tone="success" icon={<CheckCircle2 />}>
                Balanced
              </StatusBadge>
            ) : (
              <StatusBadge tone="warning" icon={<AlertTriangle />}>
                Difference {formatCurrency(data.difference)}
              </StatusBadge>
            )}
          </div>
          <p className="text-muted-foreground truncate text-xs">
            {data.accountName}
            {data.bankName && <>, {data.bankName}</>}
          </p>
          <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1.5 text-xs">
            <dt className="text-muted-foreground">Bank cashbook</dt>
            <dd className="text-foreground text-right font-mono text-sm font-medium tabular-nums">
              {formatCurrency(data.cashbookBalance)}
            </dd>
            <dt className="text-muted-foreground">Client ledgers</dt>
            <dd className="text-foreground text-right font-mono text-sm font-medium tabular-nums">
              {formatCurrency(data.clientLedgerTotal)}
            </dd>
            <dt className="text-muted-foreground">Last reconciliation</dt>
            <dd className="text-foreground text-right">{lastReconciliationText(data)}</dd>
            <dt className="text-muted-foreground">Awaiting approval</dt>
            <dd className="text-foreground text-right font-mono tabular-nums">
              {data.pendingApprovalCount}
            </dd>
          </dl>
          {data.accounts.length > 1 && (
            <p className="text-muted-foreground text-xs">
              Primary of {data.accounts.length} trust accounts
            </p>
          )}
        </>
      )}
    </WidgetCard>
  );
}
