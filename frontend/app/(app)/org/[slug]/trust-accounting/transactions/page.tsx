import { notFound } from "next/navigation";
import Link from "next/link";
import { ArrowUpRight, ArrowDownLeft, ChevronLeft, ChevronRight } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchTransactions,
  type TransactionPage,
} from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { TransactionActions } from "@/components/trust/transaction-actions";
import { ApprovalBadge } from "@/components/trust/approval-badge";
import { ReversalButton } from "@/components/trust/reversal-button";
import { TransactionFilters } from "@/components/trust/transaction-filters";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { TrustTransactionStatus, TrustTransactionType } from "@/lib/types";

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

export default async function TransactionsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{
    status?: string;
    type?: string;
    dateFrom?: string;
    dateTo?: string;
    customerId?: string;
    projectId?: string;
    page?: string;
  }>;
}) {
  const { slug } = await params;
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

  const canManageTrust =
    capData.isAdmin || capData.isOwner || capData.capabilities.includes("MANAGE_TRUST");
  const canApproveTrust =
    capData.isAdmin || capData.isOwner || capData.capabilities.includes("APPROVE_TRUST_PAYMENT");

  // Fetch primary trust account
  let accountId: string | null = null;
  let accountFetchError = false;
  try {
    const accounts = await fetchTrustAccounts();
    const primary = accounts.find((a) => a.isPrimary) ?? accounts[0];
    if (primary) {
      accountId = primary.id;
    }
  } catch {
    accountFetchError = true;
  }

  // Fetch transactions
  let transactionPage: TransactionPage | null = null;
  let fetchError = false;

  if (accountId) {
    try {
      transactionPage = await fetchTransactions(accountId, {
        dateFrom: search.dateFrom,
        dateTo: search.dateTo,
        type: search.type,
        status: search.status,
        customerId: search.customerId,
        projectId: search.projectId,
        page: search.page ? parseInt(search.page, 10) : 0,
        size: 20,
      });
    } catch {
      fetchError = true;
    }
  }

  const currentPage = transactionPage?.pageNumber ?? 0;
  const totalPages = transactionPage?.totalPages ?? 0;
  const currency = settings.defaultCurrency ?? "ZAR";

  // Build filter URL helper
  function filterUrl(overrides: Record<string, string | undefined>): string {
    const newParams = new URLSearchParams();
    const merged = { ...search, ...overrides };
    for (const [key, value] of Object.entries(merged)) {
      if (value && key !== "page") newParams.set(key, value);
    }
    const qs = newParams.toString();
    return `/org/${slug}/trust-accounting/transactions${qs ? `?${qs}` : ""}`;
  }

  function pageUrl(page: number): string {
    const newParams = new URLSearchParams();
    for (const [key, value] of Object.entries(search)) {
      if (value) newParams.set(key, value);
    }
    newParams.set("page", String(page));
    const qs = newParams.toString();
    return `/org/${slug}/trust-accounting/transactions?${qs}`;
  }

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Transactions</h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Trust account transaction history
          </p>
        </div>
        {canManageTrust && accountId && <TransactionActions accountId={accountId} slug={slug} />}
      </div>

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

      {/* Date Range, Client & Matter Filters */}
      <TransactionFilters slug={slug} search={search} />

      {/* Error State */}
      {fetchError && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Unable to load transactions. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Account Fetch Error State */}
      {accountFetchError && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Unable to load trust accounts. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {/* No Account State */}
      {!fetchError && !accountFetchError && !accountId && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No trust accounts have been set up yet.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Transactions Table */}
      {transactionPage && (
        <Card>
          <CardHeader>
            <CardTitle>Transaction History</CardTitle>
            <CardDescription>
              {transactionPage.totalElements} transaction
              {transactionPage.totalElements !== 1 ? "s" : ""} found
            </CardDescription>
          </CardHeader>
          <CardContent>
            {transactionPage.content.length === 0 ? (
              <div className="py-8 text-center">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No transactions match the current filters
                </p>
              </div>
            ) : (
              <>
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
                        <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Status
                        </th>
                        <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Actions
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {transactionPage.content.map((tx) => (
                        <tr
                          key={tx.id}
                          className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                          data-testid={`transaction-row-${tx.id}`}
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
                          <td className="py-3 pr-4">
                            <Badge variant={statusBadgeVariant(tx.status)}>
                              {tx.status.replace(/_/g, " ")}
                            </Badge>
                          </td>
                          <td className="py-3">
                            {tx.status === "AWAITING_APPROVAL" && canApproveTrust && (
                              <span data-testid={`approval-actions-${tx.id}`}>
                                <ApprovalBadge transactionId={tx.id} status={tx.status} />
                              </span>
                            )}
                            {tx.status === "APPROVED" && canManageTrust && (
                              <span data-testid={`reversal-action-${tx.id}`}>
                                <ReversalButton transactionId={tx.id} />
                              </span>
                            )}
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
