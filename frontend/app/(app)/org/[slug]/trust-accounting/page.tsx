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
  ArrowDownLeft,
} from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { AddTrustAccountButton } from "@/components/trust/AddTrustAccountButton";
import { fetchTrustAccounts, fetchDashboardData } from "./actions";
import type {
  TrustDashboardData,
  TrustTransactionStatus,
  TrustTransactionType,
  TrustAlertSeverity,
} from "@/lib/types";

// ── Helpers ────────────────────────────────────────────────────────

function formatCurrency(amount: number, currency = "ZAR"): string {
  return new Intl.NumberFormat("en-ZA", {
    style: "currency",
    currency,
  }).format(amount);
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function statusBadgeVariant(
  status: TrustTransactionStatus
): "success" | "warning" | "destructive" | "neutral" {
  switch (status) {
    case "APPROVED":
      return "success";
    case "AWAITING_APPROVAL":
      return "warning";
    case "REJECTED":
      return "destructive";
    case "RECORDED":
    case "REVERSED":
    default:
      return "neutral";
  }
}

function transactionTypeLabel(type: TrustTransactionType): string {
  return type
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function isInflowType(type: TrustTransactionType): boolean {
  return ["DEPOSIT", "TRANSFER_IN", "REFUND", "INTEREST_CREDIT"].includes(type);
}

function alertSeverityColor(severity: TrustAlertSeverity): string {
  switch (severity) {
    case "error":
      return "text-red-600 dark:text-red-400";
    case "warning":
      return "text-amber-600 dark:text-amber-400";
    case "info":
    default:
      return "text-teal-600 dark:text-teal-400";
  }
}

function AlertIcon({ severity }: { severity: TrustAlertSeverity }) {
  switch (severity) {
    case "error":
      return <XCircle className="size-4 text-red-600 dark:text-red-400" />;
    case "warning":
      return <AlertTriangle className="size-4 text-amber-600 dark:text-amber-400" />;
    case "info":
    default:
      return <Info className="size-4 text-teal-600 dark:text-teal-400" />;
  }
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
    notFound();
  }

  // Fetch trust accounts and find the primary one
  let dashboardData: TrustDashboardData | null = null;
  let accountName = "Trust Account";
  let fetchError = false;

  try {
    const accounts = await fetchTrustAccounts();
    const primary = accounts.find((a) => a.isPrimary) ?? accounts[0];

    if (primary) {
      accountName = primary.accountName;
      dashboardData = await fetchDashboardData(primary.id);
    }
  } catch (error) {
    console.error("Failed to fetch trust dashboard data:", error);
    fetchError = true;
  }

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Trust Accounting
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            LSSA-compliant trust account management for client funds
          </p>
        </div>
        {dashboardData && (
          <div className="flex items-center gap-2">
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
          </div>
        )}
      </div>

      {fetchError && (
        <Card>
          <CardContent className="py-10 text-center">
            <Scale className="mx-auto size-8 text-slate-400" />
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              Unable to load trust account data. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {!fetchError && !dashboardData && (
        <Card>
          <CardContent className="py-10 text-center">
            <Scale className="mx-auto size-8 text-slate-400" />
            <h3 className="font-display mt-3 text-lg font-semibold text-slate-950 dark:text-slate-50">
              No Trust Accounts
            </h3>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
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
          {/* Summary Cards */}
          <div
            className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
            data-testid="summary-cards"
          >
            {/* Total Balance */}
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
                  Trust Balance
                </CardTitle>
                <Wallet className="size-4 text-slate-400" />
              </CardHeader>
              <CardContent>
                <div className="font-mono text-2xl font-semibold text-slate-950 tabular-nums dark:text-slate-50">
                  {formatCurrency(dashboardData.totalBalance, settings.defaultCurrency)}
                </div>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  {accountName} cashbook balance
                </p>
              </CardContent>
            </Card>

            {/* Active Clients */}
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
                  Active Clients
                </CardTitle>
                <Users className="size-4 text-slate-400" />
              </CardHeader>
              <CardContent>
                <div className="font-mono text-2xl font-semibold text-slate-950 tabular-nums dark:text-slate-50">
                  {dashboardData.activeClients}
                </div>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Clients with trust balances
                </p>
              </CardContent>
            </Card>

            {/* Pending Approvals */}
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
                  Pending Approvals
                </CardTitle>
                <Clock className="size-4 text-slate-400" />
              </CardHeader>
              <CardContent>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-2xl font-semibold text-slate-950 tabular-nums dark:text-slate-50">
                    {dashboardData.pendingApprovals}
                  </span>
                  {dashboardData.pendingApprovals > 0 && (
                    <Badge variant="warning">Needs attention</Badge>
                  )}
                </div>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Transactions awaiting approval
                </p>
              </CardContent>
            </Card>

            {/* Last Reconciliation */}
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
                  Reconciliation
                </CardTitle>
                {dashboardData.lastReconciliationBalanced === true ? (
                  <CheckCircle2 className="size-4 text-green-600" />
                ) : dashboardData.lastReconciliationBalanced === false ? (
                  <XCircle className="size-4 text-red-600" />
                ) : (
                  <Scale className="size-4 text-slate-400" />
                )}
              </CardHeader>
              <CardContent>
                {dashboardData.lastReconciliationDate ? (
                  <>
                    <div className="flex items-center gap-2">
                      {dashboardData.lastReconciliationBalanced ? (
                        <span className="inline-flex size-2 rounded-full bg-green-500" />
                      ) : (
                        <span className="inline-flex size-2 rounded-full bg-red-500" />
                      )}
                      <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                        {dashboardData.lastReconciliationBalanced ? "Balanced" : "Unbalanced"}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      Last reconciled {formatDate(dashboardData.lastReconciliationDate)}
                    </p>
                  </>
                ) : (
                  <>
                    <div className="text-sm font-medium text-slate-500 dark:text-slate-400">
                      Not yet reconciled
                    </div>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      No reconciliation records found
                    </p>
                  </>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Alerts Section */}
          {dashboardData.alerts.length > 0 && (
            <div className="space-y-2" data-testid="alerts-section">
              {dashboardData.alerts.map((alert, index) => (
                <div
                  key={`${alert.type}-${index}`}
                  className={`flex items-center gap-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-700 ${alertSeverityColor(alert.severity)}`}
                >
                  <AlertIcon severity={alert.severity} />
                  <span className="text-sm">{alert.message}</span>
                </div>
              ))}
            </div>
          )}

          {/* Recent Transactions Table */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0">
              <div>
                <CardTitle>Recent Transactions</CardTitle>
                <CardDescription>
                  Last 10 transactions across all client ledgers
                </CardDescription>
              </div>
              <Button asChild variant="ghost" size="sm">
                <Link href={`/org/${slug}/trust-accounting/transactions`}>
                  View all
                  <ArrowUpRight className="ml-1 size-3" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent>
              {dashboardData.recentTransactions.length === 0 ? (
                <div className="py-8 text-center">
                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    No transactions recorded yet
                  </p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm" data-testid="transactions-table">
                    <thead>
                      <tr className="border-b border-slate-200 dark:border-slate-700">
                        <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Date
                        </th>
                        <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Reference
                        </th>
                        <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Type
                        </th>
                        <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                          Amount
                        </th>
                        <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Status
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {dashboardData.recentTransactions.map((tx) => (
                        <tr
                          key={tx.id}
                          className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                        >
                          <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                            {formatDate(tx.transactionDate)}
                          </td>
                          <td className="py-3 pr-4 font-medium text-slate-950 dark:text-slate-50">
                            {tx.reference}
                          </td>
                          <td className="py-3 pr-4">
                            <Badge variant="neutral">
                              {transactionTypeLabel(tx.transactionType)}
                            </Badge>
                          </td>
                          <td className="py-3 pr-4 text-right">
                            <span
                              className={`inline-flex items-center gap-1 font-mono tabular-nums ${
                                isInflowType(tx.transactionType)
                                  ? "text-green-600 dark:text-green-400"
                                  : "text-slate-950 dark:text-slate-50"
                              }`}
                            >
                              {isInflowType(tx.transactionType) ? (
                                <ArrowDownLeft className="size-3" />
                              ) : (
                                <ArrowUpRight className="size-3" />
                              )}
                              {formatCurrency(tx.amount, settings.defaultCurrency)}
                            </span>
                          </td>
                          <td className="py-3">
                            <Badge variant={statusBadgeVariant(tx.status)}>
                              {tx.status.replace(/_/g, " ")}
                            </Badge>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
