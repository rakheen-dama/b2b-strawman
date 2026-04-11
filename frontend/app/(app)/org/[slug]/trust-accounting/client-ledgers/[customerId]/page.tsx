import { notFound } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ArrowUpRight, ArrowDownLeft, ChevronLeft, ChevronRight } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchClientLedger,
  fetchClientHistory,
  type ClientHistoryPage,
} from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
import { PrintStatementButton } from "@/components/trust/print-statement-button";
import { ClientHistoryFilters } from "@/components/trust/client-history-filters";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { TrustTransactionStatus, TrustTransactionType, ClientLedgerCard } from "@/lib/types";

// ── Helpers ───────────────────────────────────────────────────────

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

const STATUS_OPTIONS: TrustTransactionStatus[] = [
  "RECORDED",
  "AWAITING_APPROVAL",
  "APPROVED",
  "REJECTED",
  "REVERSED",
];

const TYPE_OPTIONS: TrustTransactionType[] = [
  "DEPOSIT",
  "PAYMENT",
  "TRANSFER_IN",
  "TRANSFER_OUT",
  "FEE_TRANSFER",
  "REFUND",
  "INTEREST_CREDIT",
  "INTEREST_LPFF",
  "REVERSAL",
];

// ── Page ──────────────────────────────────────────────────────────

export default async function ClientDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string; customerId: string }>;
  searchParams: Promise<{
    status?: string;
    type?: string;
    dateFrom?: string;
    dateTo?: string;
    page?: string;
  }>;
}) {
  const { slug, customerId } = await params;
  const search = await searchParams;

  // Module gating
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

  // Capability check
  const capData = await fetchMyCapabilities();
  const hasViewTrust =
    capData.isAdmin || capData.isOwner || capData.capabilities.includes("VIEW_TRUST");
  if (!hasViewTrust) {
    notFound();
  }

  // Fetch primary trust account
  let accountId: string | null = null;
  try {
    const accounts = await fetchTrustAccounts();
    const primary = accounts.find((a) => a.isPrimary) ?? accounts[0];
    if (primary) {
      accountId = primary.id;
    }
  } catch {
    notFound();
  }

  if (!accountId) {
    notFound();
  }

  // Fetch client ledger card
  let ledgerCard: ClientLedgerCard | null = null;
  try {
    ledgerCard = await fetchClientLedger(accountId, customerId);
  } catch {
    notFound();
  }

  if (!ledgerCard) {
    notFound();
  }

  // Fetch transaction history
  let historyPage: ClientHistoryPage | null = null;
  let fetchError = false;
  try {
    historyPage = await fetchClientHistory(accountId, customerId, {
      status: search.status,
      type: search.type,
      dateFrom: search.dateFrom,
      dateTo: search.dateTo,
      page: search.page ? parseInt(search.page, 10) : 0,
      size: 20,
    });
  } catch {
    fetchError = true;
  }

  const currentPage = historyPage?.pageNumber ?? 0;
  const totalPages = historyPage?.totalPages ?? 0;
  const currency = settings.defaultCurrency ?? "ZAR";

  // Build filter URL helper
  function filterUrl(overrides: Record<string, string | undefined>): string {
    const newParams = new URLSearchParams();
    const merged = { ...search, ...overrides };
    for (const [key, value] of Object.entries(merged)) {
      if (value && key !== "page") newParams.set(key, value);
    }
    const qs = newParams.toString();
    return `/org/${slug}/trust-accounting/client-ledgers/${customerId}${qs ? `?${qs}` : ""}`;
  }

  function pageUrl(page: number): string {
    const newParams = new URLSearchParams();
    for (const [key, value] of Object.entries(search)) {
      if (value) newParams.set(key, value);
    }
    newParams.set("page", String(page));
    const qs = newParams.toString();
    return `/org/${slug}/trust-accounting/client-ledgers/${customerId}?${qs}`;
  }

  // Compute running balance from transaction history
  // The backend history endpoint returns transactions in descending order.
  // Running balance is only accurate on page 0 where we can start from the
  // current total balance and work backward. On subsequent pages the starting
  // point is unknown (the backend doesn't return a startingBalance), so we
  // show "—" instead of potentially incorrect numbers.
  const showRunningBalance = currentPage === 0;
  const transactionsWithBalance = (() => {
    if (!historyPage || historyPage.content.length === 0) return [];
    if (!showRunningBalance) {
      return historyPage.content.map((tx) => ({
        ...tx,
        runningBalance: null as number | null,
      }));
    }
    // Page 0: approximate running balance using the client's current balance
    // and working backward from the latest transaction
    const txs = [...historyPage.content];
    let balance = ledgerCard.balance;
    const result = txs.map((tx) => {
      const currentBalance = balance;
      // Working backward: reverse the effect of this transaction
      if (isInflowType(tx.transactionType)) {
        balance -= tx.amount;
      } else {
        balance += tx.amount;
      }
      return { ...tx, runningBalance: currentBalance as number | null };
    });
    return result;
  })();

  return (
    <div className="space-y-8" data-testid="client-detail-page">
      {/* Back link + Header */}
      <div>
        <Link
          href={`/org/${slug}/trust-accounting/client-ledgers`}
          className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
          data-testid="back-link"
        >
          <ArrowLeft className="size-4" />
          Back to Client Ledgers
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          {ledgerCard.customerName}
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Client ledger detail and transaction history
        </p>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" data-testid="client-summary">
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm font-medium text-slate-500 dark:text-slate-400">Trust Balance</p>
            <p className="mt-1 text-2xl font-bold text-slate-950 dark:text-slate-50">
              {formatCurrency(ledgerCard.balance, currency)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm font-medium text-slate-500 dark:text-slate-400">Total Deposits</p>
            <p className="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
              {formatCurrency(ledgerCard.totalDeposits, currency)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm font-medium text-slate-500 dark:text-slate-400">Total Payments</p>
            <p className="mt-1 text-2xl font-bold text-slate-950 dark:text-slate-50">
              {formatCurrency(ledgerCard.totalPayments, currency)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm font-medium text-slate-500 dark:text-slate-400">
              Total Fee Transfers
            </p>
            <p className="mt-1 text-2xl font-bold text-slate-950 dark:text-slate-50">
              {formatCurrency(ledgerCard.totalFeeTransfers, currency)}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Print Statement */}
      <Card>
        <CardHeader>
          <CardTitle>Generate Statement</CardTitle>
          <CardDescription>
            Select a date range and generate a PDF statement for this client
          </CardDescription>
        </CardHeader>
        <CardContent>
          <PrintStatementButton accountId={accountId} customerId={customerId} />
        </CardContent>
      </Card>

      {/* Status Filter Pills */}
      <div className="flex flex-wrap items-center gap-3" data-testid="status-filters">
        <span className="text-sm font-medium text-slate-600 dark:text-slate-400">Status:</span>
        <Link
          href={filterUrl({ status: undefined })}
          className={`rounded-full px-3 py-1 text-sm transition-colors ${
            !search.status
              ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
              : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
          }`}
        >
          All
        </Link>
        {STATUS_OPTIONS.map((status) => (
          <Link
            key={status}
            href={filterUrl({ status })}
            className={`rounded-full px-3 py-1 text-sm transition-colors ${
              search.status === status
                ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
                : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
            }`}
          >
            {status === "AWAITING_APPROVAL"
              ? "Awaiting Approval"
              : status.charAt(0) + status.slice(1).toLowerCase()}
          </Link>
        ))}
      </div>

      {/* Type Filter Pills */}
      <div className="flex flex-wrap items-center gap-3" data-testid="type-filters">
        <span className="text-sm font-medium text-slate-600 dark:text-slate-400">Type:</span>
        <Link
          href={filterUrl({ type: undefined })}
          className={`rounded-full px-3 py-1 text-sm transition-colors ${
            !search.type
              ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
              : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
          }`}
        >
          All
        </Link>
        {TYPE_OPTIONS.map((type) => (
          <Link
            key={type}
            href={filterUrl({ type })}
            className={`rounded-full px-3 py-1 text-sm transition-colors ${
              search.type === type
                ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
                : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
            }`}
          >
            {transactionTypeLabel(type)}
          </Link>
        ))}
      </div>

      {/* Date Range Filters */}
      <ClientHistoryFilters slug={slug} customerId={customerId} search={search} />

      {/* Error State */}
      {fetchError && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Unable to load transaction history. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Transaction History Table */}
      {historyPage && (
        <Card>
          <CardHeader>
            <CardTitle>Transaction History</CardTitle>
            <CardDescription>
              {historyPage.totalElements} transaction
              {historyPage.totalElements !== 1 ? "s" : ""} found
            </CardDescription>
          </CardHeader>
          <CardContent>
            {historyPage.content.length === 0 ? (
              <div className="py-8 text-center">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No transactions match the current filters
                </p>
              </div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm" data-testid="history-table">
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
                        <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Status
                        </th>
                        <th className="pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                          Running Balance
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {transactionsWithBalance.map((tx) => (
                        <tr
                          key={tx.id}
                          className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                          data-testid={`history-row-${tx.id}`}
                        >
                          <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                            {formatLocalDate(tx.transactionDate)}
                          </td>
                          <td className="py-3 pr-4 font-medium text-slate-950 dark:text-slate-50">
                            {tx.reference}
                          </td>
                          <td className="py-3 pr-4">
                            <Badge variant="neutral">
                              {transactionTypeLabel(tx.transactionType as TrustTransactionType)}
                            </Badge>
                          </td>
                          <td className="py-3 pr-4 text-right">
                            <span
                              className={`inline-flex items-center gap-1 font-mono tabular-nums ${
                                isInflowType(tx.transactionType as TrustTransactionType)
                                  ? "text-green-600 dark:text-green-400"
                                  : "text-slate-950 dark:text-slate-50"
                              }`}
                            >
                              {isInflowType(tx.transactionType as TrustTransactionType) ? (
                                <ArrowDownLeft className="size-3" />
                              ) : (
                                <ArrowUpRight className="size-3" />
                              )}
                              {formatCurrency(tx.amount, currency)}
                            </span>
                          </td>
                          <td className="py-3 pr-4">
                            <Badge
                              variant={statusBadgeVariant(tx.status as TrustTransactionStatus)}
                            >
                              {tx.status.replace(/_/g, " ")}
                            </Badge>
                          </td>
                          <td className="py-3 text-right font-mono text-slate-950 tabular-nums dark:text-slate-50">
                            {tx.runningBalance !== null
                              ? formatCurrency(tx.runningBalance, currency)
                              : "\u2014"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Pagination */}
                {totalPages > 1 && (
                  <div className="mt-4 flex items-center justify-between border-t border-slate-200 pt-4 dark:border-slate-700">
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      Page {currentPage + 1} of {totalPages}
                    </p>
                    <div className="flex items-center gap-2">
                      {currentPage > 0 ? (
                        <Button asChild variant="outline" size="sm">
                          <Link href={pageUrl(currentPage - 1)}>
                            <ChevronLeft className="mr-1 size-4" />
                            Previous
                          </Link>
                        </Button>
                      ) : (
                        <Button variant="outline" size="sm" disabled>
                          <ChevronLeft className="mr-1 size-4" />
                          Previous
                        </Button>
                      )}
                      {currentPage < totalPages - 1 ? (
                        <Button asChild variant="outline" size="sm">
                          <Link href={pageUrl(currentPage + 1)}>
                            Next
                            <ChevronRight className="ml-1 size-4" />
                          </Link>
                        </Button>
                      ) : (
                        <Button variant="outline" size="sm" disabled>
                          Next
                          <ChevronRight className="ml-1 size-4" />
                        </Button>
                      )}
                    </div>
                  </div>
                )}
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
