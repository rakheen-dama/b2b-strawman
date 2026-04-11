import { notFound } from "next/navigation";
import Link from "next/link";
import { ChevronLeft, ChevronRight, Users } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchClientLedgers,
  type ClientLedgerPage,
} from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";

// ── Page ──────────────────────────────────────────────────────────

export default async function ClientLedgersPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{
    nonZeroOnly?: string;
    search?: string;
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

  // Fetch client ledgers
  let ledgerPage: ClientLedgerPage | null = null;
  let fetchError = false;

  if (accountId) {
    try {
      ledgerPage = await fetchClientLedgers(accountId, {
        page: search.page ? parseInt(search.page, 10) : 0,
        size: 20,
        nonZeroOnly: search.nonZeroOnly === "true",
        search: search.search,
      });
    } catch {
      fetchError = true;
    }
  }

  const currentPage = ledgerPage?.pageNumber ?? 0;
  const totalPages = ledgerPage?.totalPages ?? 0;
  const currency = settings.defaultCurrency ?? "ZAR";

  // Build filter URL helper
  function filterUrl(overrides: Record<string, string | undefined>): string {
    const newParams = new URLSearchParams();
    const merged = { ...search, ...overrides };
    for (const [key, value] of Object.entries(merged)) {
      if (value && key !== "page") newParams.set(key, value);
    }
    const qs = newParams.toString();
    return `/org/${slug}/trust-accounting/client-ledgers${qs ? `?${qs}` : ""}`;
  }

  function pageUrl(page: number): string {
    const newParams = new URLSearchParams();
    for (const [key, value] of Object.entries(search)) {
      if (value) newParams.set(key, value);
    }
    newParams.set("page", String(page));
    const qs = newParams.toString();
    return `/org/${slug}/trust-accounting/client-ledgers?${qs}`;
  }

  return (
    <div className="space-y-8" data-testid="client-ledgers-page">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Client Ledgers
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Trust account balances by client
          </p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-4" data-testid="ledger-filters">
        <Link
          href={filterUrl({
            nonZeroOnly: search.nonZeroOnly === "true" ? undefined : "true",
          })}
          className={`rounded-full px-3 py-1 text-sm transition-colors ${
            search.nonZeroOnly === "true"
              ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
              : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
          }`}
          data-testid="non-zero-filter"
        >
          Non-zero balances only
        </Link>

        <form
          action={`/org/${slug}/trust-accounting/client-ledgers`}
          method="GET"
          className="flex items-center gap-2"
        >
          {search.nonZeroOnly === "true" && <input type="hidden" name="nonZeroOnly" value="true" />}
          <input
            type="text"
            name="search"
            placeholder="Search clients..."
            defaultValue={search.search ?? ""}
            className="h-8 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-500 focus:ring-1 focus:ring-teal-500 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder:text-slate-500"
            data-testid="client-search"
          />
          <Button type="submit" variant="outline" size="sm">
            Search
          </Button>
        </form>
      </div>

      {/* No Account State */}
      {!accountId && !accountFetchError && (
        <Card>
          <CardContent className="py-10 text-center">
            <Users className="mx-auto mb-3 size-10 text-slate-300 dark:text-slate-600" />
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No trust account configured. Set up a trust account to view client ledgers.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Error State */}
      {(fetchError || accountFetchError) && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400" data-testid="error-state">
              Unable to load client ledgers. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Client Ledger Table */}
      {ledgerPage && (
        <Card>
          <CardHeader>
            <CardTitle>Client Balances</CardTitle>
            <CardDescription>
              {ledgerPage.content.length} client
              {ledgerPage.content.length !== 1 ? "s" : ""} found
            </CardDescription>
          </CardHeader>
          <CardContent>
            {ledgerPage.content.length === 0 ? (
              <div className="py-8 text-center" data-testid="empty-state">
                <Users className="mx-auto mb-3 size-10 text-slate-300 dark:text-slate-600" />
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No client ledgers match the current filters
                </p>
              </div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm" data-testid="client-ledgers-table">
                    <thead>
                      <tr className="border-b border-slate-200 dark:border-slate-700">
                        <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Client Name
                        </th>
                        <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                          Trust Balance
                        </th>
                        <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                          Total Deposits
                        </th>
                        <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                          Total Payments
                        </th>
                        <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                          Total Fee Transfers
                        </th>
                        <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Last Transaction
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {ledgerPage.content.map((ledger) => (
                        <tr
                          key={ledger.customerId}
                          className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                          data-testid={`ledger-row-${ledger.customerId}`}
                        >
                          <td className="py-3 pr-4">
                            <Link
                              href={`/org/${slug}/trust-accounting/client-ledgers/${ledger.customerId}`}
                              className="font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                            >
                              {ledger.customerName}
                            </Link>
                          </td>
                          <td className="py-3 pr-4 text-right font-mono text-slate-950 tabular-nums dark:text-slate-50">
                            {formatCurrency(ledger.balance, currency)}
                          </td>
                          <td className="py-3 pr-4 text-right font-mono text-slate-700 tabular-nums dark:text-slate-300">
                            {formatCurrency(ledger.totalDeposits, currency)}
                          </td>
                          <td className="py-3 pr-4 text-right font-mono text-slate-700 tabular-nums dark:text-slate-300">
                            {formatCurrency(ledger.totalPayments, currency)}
                          </td>
                          <td className="py-3 pr-4 text-right font-mono text-slate-700 tabular-nums dark:text-slate-300">
                            {formatCurrency(ledger.totalFeeTransfers, currency)}
                          </td>
                          <td className="py-3 text-slate-700 dark:text-slate-300">
                            {ledger.lastTransactionDate
                              ? formatLocalDate(ledger.lastTransactionDate)
                              : "No transactions"}
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
