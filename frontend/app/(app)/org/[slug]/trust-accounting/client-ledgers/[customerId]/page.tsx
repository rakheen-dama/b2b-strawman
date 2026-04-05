import { notFound } from "next/navigation";
import Link from "next/link";
import {
  ArrowUpRight,
  ArrowDownLeft,
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  FileText,
} from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchClientLedger,
  fetchClientHistory,
  type ClientHistoryPage,
} from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type {
  TrustTransactionStatus,
  TrustTransactionType,
  ClientLedgerResponse,
} from "@/lib/types";

// ── Helpers ───────────────────────────────────────────────────────

function statusBadgeVariant(
  status: TrustTransactionStatus,
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
  return ["DEPOSIT", "TRANSFER_IN", "REFUND", "INTEREST_CREDIT"].includes(
    type,
  );
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

export default async function ClientLedgerDetailPage({
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
    capData.isAdmin ||
    capData.isOwner ||
    capData.capabilities.includes("VIEW_TRUST");
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

  // Fetch client ledger summary
  let ledger: ClientLedgerResponse | null = null;
  try {
    ledger = await fetchClientLedger(accountId, customerId);
  } catch {
    notFound();
  }

  if (!ledger) {
    notFound();
  }

  // Fetch transaction history
  let historyPage: ClientHistoryPage | null = null;
  let fetchError = false;

  try {
    historyPage = await fetchClientHistory(accountId, customerId, {
      dateFrom: search.dateFrom,
      dateTo: search.dateTo,
      type: search.type,
      status: search.status,
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

  // Print statement URL
  const statementUrl = `/org/${slug}/trust-accounting/reports?report=client-statement&customerId=${customerId}&accountId=${accountId}`;

  return (
    <div className="space-y-8">
      {/* Back link + Header */}
      <div>
        <Link
          href={`/org/${slug}/trust-accounting/client-ledgers`}
          className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300"
        >
          <ArrowLeft className="size-4" />
          Back to Client Ledgers
        </Link>
        <div className="flex items-center justify-between">
          <div>
            <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
              {ledger.customerName}
            </h1>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Client trust account ledger
            </p>
          </div>
          <Button asChild variant="outline">
            <a href={statementUrl} data-testid="print-statement-btn">
              <FileText className="mr-2 size-4" />
              Print Statement
            </a>
          </Button>
        </div>
      </div>

      {/* Summary Cards */}
      <div
        className="grid grid-cols-2 gap-4 md:grid-cols-4"
        data-testid="client-summary"
      >
        <Card>
          <CardContent className="p-4">
            <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
              Trust Balance
            </p>
            <p className="mt-1 text-xl font-semibold text-slate-950 dark:text-slate-50">
              {formatCurrency(ledger.balance, currency)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
              Total Deposits
            </p>
            <p className="mt-1 text-xl font-semibold text-green-600 dark:text-green-400">
              {formatCurrency(ledger.totalDeposits, currency)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
              Total Payments
            </p>
            <p className="mt-1 text-xl font-semibold text-slate-950 dark:text-slate-50">
              {formatCurrency(ledger.totalPayments, currency)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
              Fee Transfers
            </p>
            <p className="mt-1 text-xl font-semibold text-slate-950 dark:text-slate-50">
              {formatCurrency(ledger.totalFeeTransfers, currency)}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Status Filter Pills */}
      <div
        className="flex flex-wrap items-center gap-3"
        data-testid="status-filters"
      >
        <span className="text-sm font-medium text-slate-600 dark:text-slate-400">
          Status:
        </span>
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
        <span className="text-sm font-medium text-slate-600 dark:text-slate-400">
          Type:
        </span>
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

      {/* Transaction History Table */}
      {fetchError && (
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-sm text-red-600 dark:text-red-400">
              Failed to load transaction history. Please try again.
            </p>
          </CardContent>
        </Card>
      )}

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
                  <table
                    className="w-full text-sm"
                    data-testid="client-transactions-table"
                  >
                    <thead>
                      <tr className="border-b border-slate-200 dark:border-slate-700">
                        <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                          Date
                        </th>
                        <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                          Reference
                        </th>
                        <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                          Type
                        </th>
                        <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                          Amount
                        </th>
                        <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Status
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {historyPage.content.map((tx) => (
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
                              {formatCurrency(tx.amount, currency)}
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
