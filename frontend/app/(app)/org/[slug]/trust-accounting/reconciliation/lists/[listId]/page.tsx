import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, Inbox, Lock } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@b2mash/ui/card";
import { PageHeader } from "@b2mash/ui/page-header";
import { StatCard } from "@b2mash/ui/stat-card";
import { ApiError } from "@/lib/api";
import { rethrowOnExpiredSession } from "@/lib/auth/expiry-server";
import { fetchMonthlyList } from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import { EmptyState } from "@/components/empty-state";
import {
  MonthlyListAfterLockTable,
  MonthlyListCreditorsTable,
} from "@/components/trust/monthly-list-tables";
import { formatCurrency, formatDateTime, formatLocalDate } from "@/lib/format";
import { integrityFlagLabel, integritySeverityLabel } from "@/lib/trust-integrity";
import type { MonthlyList } from "@/lib/types";

/** Currency of the trust books (the reconciliation pages use ZAR throughout). */
const CURRENCY = "ZAR";

/**
 * One locked Rule 54.15 monthly trust creditor list (Phase 92 650A, ADR-382): what was compared and
 * frozen when the month's reconciliation completed — the totals, every creditor by name, the flags
 * — and, separately, any entry made after the lock and dated in the month. Read-only. The
 * reconciliation layout gates the trust module and VIEW_TRUST.
 */
export default async function MonthlyListPage({
  params,
}: {
  params: Promise<{ slug: string; listId: string }>;
}) {
  const { slug, listId } = await params;
  const backHref = `/org/${slug}/trust-accounting/reconciliation`;

  let list: MonthlyList | null = null;
  try {
    list = await fetchMonthlyList(listId);
  } catch (error) {
    // A re-login redirect thrown deeper down propagates, and a 401 goes to the re-login flow;
    // a 404 is the not-found page; anything else shows the load-error card below
    await rethrowOnExpiredSession(error);
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
  }

  if (!list) {
    return (
      <div className="space-y-8">
        <PageHeader title="Trust creditors list" />
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-sm text-red-600 dark:text-red-400" data-testid="monthly-list-error">
              Failed to load the monthly list. Please try again.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  const adjustments = list.adjustmentsSinceLock;

  return (
    <div className="space-y-8" data-testid="monthly-list-page">
      <PageHeader
        title={`Trust creditors list — ${formatLocalDate(list.periodEnd)}`}
        description={`${list.trustAccountName ?? "Trust account"} · Locked ${formatDateTime(list.lockedAt)}${
          list.lockedByName ? ` by ${list.lockedByName}` : ""
        } · Rule 54.15`}
        actions={
          <Button asChild variant="outline" size="sm">
            <Link href={backHref}>
              <ArrowLeft className="mr-1 size-4" />
              Reconciliation
            </Link>
          </Button>
        }
      />

      <div className="@container">
        <div className="grid grid-cols-1 gap-4 @xl:grid-cols-2 @4xl:grid-cols-4">
          <StatCard
            label="Adjusted bank balance"
            value={formatCurrency(list.adjustedBankBalance, CURRENCY)}
          />
          <StatCard label="Cashbook" value={formatCurrency(list.cashbookBalance, CURRENCY)} />
          <StatCard label="Trust creditors" value={formatCurrency(list.creditorTotal, CURRENCY)} />
          <StatCard label="Difference" value={formatCurrency(list.difference, CURRENCY)} />
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Lock className="size-4 text-teal-600 dark:text-teal-400" />
            Trust creditors as at {formatLocalDate(list.periodEnd)}
          </CardTitle>
          <CardDescription>
            Frozen when the reconciliation completed. Trust cash, estates and other trust assets are
            not recorded in Kazi and are not included in this comparison.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <MonthlyListCreditorsTable
            lines={list.lines}
            creditorTotal={list.creditorTotal}
            currency={CURRENCY}
            caption={`Trust creditors as at ${formatLocalDate(list.periodEnd)}`}
          />
        </CardContent>
      </Card>

      {list.integrityFlags.length > 0 && (
        <Card data-testid="monthly-list-flags">
          <CardHeader>
            <CardTitle>Flags at completion</CardTitle>
            <CardDescription>
              What the trust books check showed when the list was locked
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {list.integrityFlags.map((flag, index) => (
                <li key={`${flag.code}-${index}`} className="text-sm">
                  <div className="flex items-center gap-2">
                    <span className="text-foreground font-medium">
                      {integrityFlagLabel(flag.code)}
                    </span>
                    <Badge variant={flag.severity === "BLOCKING" ? "destructive" : "warning"}>
                      {integritySeverityLabel(flag.severity)}
                    </Badge>
                  </div>
                  <p className="text-muted-foreground mt-1">{flag.message}</p>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      <Card data-testid="monthly-list-after-lock">
        <CardHeader>
          <CardTitle>Entered after this list was locked</CardTitle>
          <CardDescription>
            Entries dated on or before {formatLocalDate(list.periodEnd)} and recorded after the list
            was locked. They are part of the books, not of this list.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <MonthlyListAfterLockTable
            entries={adjustments.entries}
            currency={CURRENCY}
            empty={
              <EmptyState
                variant="inline"
                icon={Inbox}
                title="Nothing has been entered after this list was locked."
                description={null}
              />
            }
            footer={
              <p className="text-foreground text-sm">
                Trust creditors as at {formatLocalDate(list.periodEnd)} are now{" "}
                <span className="font-mono tabular-nums">
                  {formatCurrency(adjustments.liveCreditorTotal, CURRENCY)}
                </span>
                , a change of{" "}
                <span className="font-mono tabular-nums">
                  {formatCurrency(adjustments.changeInCreditorTotal, CURRENCY)}
                </span>{" "}
                from the locked list.
              </p>
            }
          />
        </CardContent>
      </Card>
    </div>
  );
}
