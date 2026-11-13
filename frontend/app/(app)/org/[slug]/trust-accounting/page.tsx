import Link from "next/link";
import { notFound } from "next/navigation";
import {
  Scale,
  Wallet,
  Users,
  Clock,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Info,
  ArrowUpRight,
} from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { PageHeader } from "@b2mash/ui/page-header";
import { StatCard } from "@b2mash/ui/stat-card";
import { AddTrustAccountButton } from "@/components/trust/AddTrustAccountButton";
import { TrustBalanceTrendChart } from "@/components/trust/trust-balance-trend-chart";
import { TrustTransactionsTable } from "@/components/trust/trust-transactions-table";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { draftReconciliationText } from "@/lib/trust-reconciliation-copy";
import type { WidgetResult } from "@/lib/dashboard-home";
import type { TrustBalanceTrendResponse, TrustPositionResponse } from "@/lib/dashboard-types";
import {
  fetchTrustAccounts,
  fetchDashboardData,
  fetchTrustPosition,
  fetchTrustBalanceTrend,
} from "./actions";
import type { TrustAccount, TrustAlertSeverity, TrustDashboardData } from "@/lib/types";

// ── Helpers ────────────────────────────────────────────────────────

function alertSeverityColor(severity: TrustAlertSeverity): string {
  switch (severity) {
    case "error":
      return "text-danger-foreground";
    case "warning":
      return "text-warning-foreground";
    case "info":
    default:
      return "text-info-foreground";
  }
}

function AlertIcon({ severity }: { severity: TrustAlertSeverity }) {
  switch (severity) {
    case "error":
      return <XCircle aria-hidden="true" className="size-4" />;
    case "warning":
      return <AlertTriangle aria-hidden="true" className="size-4" />;
    case "info":
    default:
      return <Info aria-hidden="true" className="size-4" />;
  }
}

/** Half a cent either way is a rounding artefact, not a difference (Home widget rule). */
function isBalanced(difference: number): boolean {
  return Math.abs(difference) < 0.005;
}

function daysAgo(days: number): string {
  return days === 1 ? "1 day ago" : `${days} days ago`;
}

/**
 * "Last reconciled 31 Oct 2026 · 13 days ago"; a DRAFT is not yet reconciled, so it reads as the
 * Home widget does — "Period to 13 Nov 2026, in draft" (no days-ago).
 */
function lastReconciledText(position: TrustPositionResponse, periodEnd: string): string {
  if (position.lastReconciliationStatus === "DRAFT") return draftReconciliationText(periodEnd);
  const parts = [`Last reconciled ${formatLocalDate(periodEnd)}`];
  if (position.daysSinceReconciliation !== null && position.daysSinceReconciliation >= 0) {
    parts.push(daysAgo(position.daysSinceReconciliation));
  }
  return parts.join(" · ");
}

function TrustBalanceHint({
  position,
  currency,
}: {
  position: TrustPositionResponse;
  currency: string;
}) {
  const balanced = isBalanced(position.difference);
  return (
    <div className="space-y-0.5">
      <p>{`${position.accountName} cashbook balance`}</p>
      <p className="flex items-center gap-1">
        {balanced ? (
          <CheckCircle2 aria-hidden="true" className="text-success-foreground size-3 shrink-0" />
        ) : (
          <AlertTriangle aria-hidden="true" className="text-danger-foreground size-3 shrink-0" />
        )}
        <span>Client ledgers {formatCurrency(position.clientLedgerTotal, currency)}</span>
        {balanced ? <span className="sr-only">(agrees with the cashbook)</span> : null}
      </p>
      {balanced ? null : (
        <p className="text-danger-foreground">
          Difference {formatCurrency(position.difference, currency)}
        </p>
      )}
    </div>
  );
}

function ReconciliationValue({ position }: { position: TrustPositionResponse }) {
  if (!position.lastReconciliationPeriodEnd) {
    return <span className="text-muted-foreground font-sans text-base">Not yet reconciled</span>;
  }
  const balanced = position.lastReconciliationBalanced === true;
  return (
    <span
      className={`inline-flex items-center gap-2 font-sans text-base ${
        balanced ? "text-success-foreground" : "text-danger-foreground"
      }`}
    >
      {balanced ? (
        <CheckCircle2 aria-hidden="true" className="size-4 shrink-0" />
      ) : (
        <XCircle aria-hidden="true" className="size-4 shrink-0" />
      )}
      {balanced ? "Balanced" : "Unbalanced"}
    </span>
  );
}

// ── Page ───────────────────────────────────────────────────────────

export default async function TrustAccountingPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("trust_accounting")) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <h2 className="font-display text-foreground text-xl font-semibold">Module Not Available</h2>
        <p className="text-muted-foreground mt-2 text-sm">
          The Trust Accounting module is not enabled for your organization.
        </p>
      </div>
    );
  }

  const currency = settings.defaultCurrency;
  let accounts: TrustAccount[] = [];
  let fetchError = false;
  try {
    accounts = await fetchTrustAccounts();
  } catch (error) {
    console.error("Failed to fetch trust accounts:", error);
    fetchError = true;
  }
  const hasTrustAccounts = accounts.length > 0;
  const primary = accounts.find((a) => a.isPrimary) ?? accounts[0];

  let dashboardData: TrustDashboardData | null = null;
  let position: WidgetResult<TrustPositionResponse> = { state: "absent" };
  let trend: WidgetResult<TrustBalanceTrendResponse> = { state: "absent" };
  if (primary) {
    // The tiles' cashbook / ledger / pending / reconciliation figures come from trust-position
    // (the Home widget's read, 643A); Active Clients, alerts and recent transactions from the
    // dashboard aggregation. The two settled reads never throw except to re-login (401).
    [dashboardData, position, trend] = await Promise.all([
      fetchDashboardData(primary.id).catch((dashboardErr: unknown) => {
        // Dashboard payload fetch failed, but the account list loaded. We still want the
        // header CTAs visible so users can reach Client Ledgers / Record Transaction,
        // which have their own data-fetching and may be usable independently.
        console.error("Failed to fetch trust dashboard data:", dashboardErr);
        fetchError = true;
        return null;
      }),
      fetchTrustPosition(primary.id),
      fetchTrustBalanceTrend(primary.id, 12),
    ]);
  }
  const positionData = position.state === "ok" ? position.data : null;
  const pendingApprovals = positionData?.pendingApprovalCount ?? 0;

  return (
    <div className="space-y-8">
      <PageHeader
        title="Trust Accounting"
        description="LSSA-compliant trust account management for client funds"
        actions={
          hasTrustAccounts ? (
            // Show nav CTAs whenever at least one trust account exists, even if the
            // dashboard payload fetch failed. The Client Ledgers and Transactions pages
            // do their own data loading and remain usable independently. PR #1004 review.
            <>
              <Button asChild variant="outline">
                <Link href={`/org/${slug}/trust-accounting/client-ledgers`}>
                  <Users className="mr-2 size-4" />
                  Client Ledgers
                </Link>
              </Button>
              <Button asChild>
                <Link href={`/org/${slug}/trust-accounting/transactions`}>
                  <ArrowUpRight className="mr-2 size-4" />
                  Record Transaction
                </Link>
              </Button>
            </>
          ) : undefined
        }
      />

      {fetchError && (
        <Card>
          <CardContent className="py-10 text-center">
            <Scale aria-hidden="true" className="text-muted-foreground mx-auto size-8" />
            <p className="text-muted-foreground mt-3 text-sm">
              Unable to load trust account data. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {!fetchError && !dashboardData && (
        <Card>
          <CardContent className="py-10 text-center">
            <Scale aria-hidden="true" className="text-muted-foreground mx-auto size-8" />
            <h3 className="font-display text-foreground mt-3 text-lg font-semibold">
              No Trust Accounts
            </h3>
            <p className="text-muted-foreground mt-1 text-sm">
              No trust accounts have been set up yet. Create your first trust account to get
              started.
            </p>
            <div className="mt-4 flex justify-center">
              <AddTrustAccountButton />
            </div>
          </CardContent>
        </Card>
      )}

      {dashboardData && (
        <>
          {/* Summary Cards — labels frozen (e2e day-07-14-work-trust) */}
          <div
            className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
            data-testid="summary-cards"
          >
            <StatCard
              label="Trust Balance"
              icon={<Wallet />}
              state={positionData ? "ready" : "error"}
              value={positionData ? formatCurrency(positionData.cashbookBalance, currency) : null}
              hint={
                positionData ? (
                  <TrustBalanceHint position={positionData} currency={currency} />
                ) : null
              }
            />

            <StatCard
              label="Active Clients"
              icon={<Users />}
              value={dashboardData.activeClients}
              hint="Clients with trust balances"
            />

            <StatCard
              label="Pending Approvals"
              icon={<Clock />}
              state={positionData ? "ready" : "error"}
              value={positionData ? pendingApprovals : null}
              hint={
                <span className="flex flex-wrap items-center gap-2">
                  <span>Transactions awaiting approval</span>
                  {pendingApprovals > 0 ? <Badge variant="warning">Needs attention</Badge> : null}
                </span>
              }
            />

            <StatCard
              label="Reconciliation"
              icon={<Scale />}
              state={positionData ? "ready" : "error"}
              value={positionData ? <ReconciliationValue position={positionData} /> : null}
              hint={
                positionData
                  ? positionData.lastReconciliationPeriodEnd
                    ? lastReconciledText(positionData, positionData.lastReconciliationPeriodEnd)
                    : "No reconciliation records found"
                  : null
              }
            />
          </div>

          {/* Alerts Section */}
          {dashboardData.alerts.length > 0 && (
            <div className="space-y-2" data-testid="alerts-section">
              {dashboardData.alerts.map((alert, index) => (
                <div
                  key={`${alert.type}-${index}`}
                  className={`border-border flex items-center gap-3 rounded-lg border px-4 py-3 ${alertSeverityColor(alert.severity)}`}
                >
                  <AlertIcon severity={alert.severity} />
                  <span className="text-sm">{alert.message}</span>
                </div>
              ))}
            </div>
          )}

          {/* Balance trend (below the fold at 1024×768 — loaded client-side, D5) */}
          <TrustBalanceTrendChart
            trend={trend.state === "ok" ? trend.data : null}
            failed={trend.state === "error"}
          />

          {/* Recent Transactions Table */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0">
              <div>
                <CardTitle>Recent Transactions</CardTitle>
                <CardDescription>Last 10 transactions across all client ledgers</CardDescription>
              </div>
              <Button asChild variant="ghost" size="sm">
                <Link href={`/org/${slug}/trust-accounting/transactions`}>
                  View all
                  <ArrowUpRight className="ml-1 size-3" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent>
              <TrustTransactionsTable
                rows={dashboardData.recentTransactions}
                currency={currency}
                empty={
                  <div className="py-8 text-center">
                    <p className="text-muted-foreground text-sm">No transactions recorded yet</p>
                  </div>
                }
              />
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
