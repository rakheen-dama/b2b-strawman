import { notFound } from "next/navigation";
import Link from "next/link";
import {
  ChevronLeft,
  ChevronRight,
  Plus,
  CheckCircle2,
  AlertCircle,
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
  fetchReconciliations,
  type ReconciliationPage,
} from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";

// ── Page ────────────────────────────��─────────────────────────────

export default async function ReconciliationListPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{
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
    capData.isAdmin ||
    capData.isOwner ||
    capData.capabilities.includes("VIEW_TRUST");
  if (!hasViewTrust) {
    notFound();
  }

  const canManageTrust =
    capData.isAdmin ||
    capData.isOwner ||
    capData.capabilities.includes("MANAGE_TRUST");

  // Fetch primary trust account
  let accountId: string | null = null;
  try {
    const accounts = await fetchTrustAccounts();
    const primary = accounts.find((a) => a.isPrimary) ?? accounts[0];
    if (primary) {
      accountId = primary.id;
    }
  } catch {
    // ignore
  }

  // Fetch reconciliations
  let reconciliationPage: ReconciliationPage | null = null;
  let fetchError = false;

  if (accountId) {
    try {
      reconciliationPage = await fetchReconciliations(accountId, {
        page: search.page ? parseInt(search.page, 10) : 0,
        size: 20,
      });
    } catch {
      fetchError = true;
    }
  }

  const currentPage = reconciliationPage?.pageNumber ?? 0;
  const totalPages = reconciliationPage?.totalPages ?? 0;
  const currency = settings.defaultCurrency ?? "ZAR";

  function pageUrl(page: number): string {
    const newParams = new URLSearchParams();
    if (search.page) newParams.set("page", search.page);
    newParams.set("page", String(page));
    const qs = newParams.toString();
    return `/org/${slug}/trust-accounting/reconciliation?${qs}`;
  }

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Reconciliation
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Trust account bank reconciliation history
          </p>
        </div>
        {canManageTrust && accountId && (
          <Button asChild>
            <Link
              href={`/org/${slug}/trust-accounting/reconciliation/new`}
              data-testid="new-reconciliation-btn"
            >
              <Plus className="mr-2 size-4" />
              New Reconciliation
            </Link>
          </Button>
        )}
      </div>

      {/* Error states */}
      {!accountId && !fetchError && (
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No trust account found. Create a trust account first.
            </p>
          </CardContent>
        </Card>
      )}

      {fetchError && (
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-sm text-red-600 dark:text-red-400">
              Failed to load reconciliations. Please try again.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Reconciliation Table */}
      {reconciliationPage && (
        <Card>
          <CardHeader>
            <CardTitle>Reconciliation History</CardTitle>
            <CardDescription>
              {reconciliationPage.totalElements} reconciliation
              {reconciliationPage.totalElements !== 1 ? "s" : ""} found
            </CardDescription>
          </CardHeader>
          <CardContent>
            {reconciliationPage.content.length === 0 ? (
              <div className="py-8 text-center">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No reconciliations yet. Start a new reconciliation to begin.
                </p>
              </div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table
                    className="w-full text-sm"
                    data-testid="reconciliations-table"
                  >
                    <thead>
                      <tr className="border-b border-slate-200 dark:border-slate-700">
                        <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                          Period
                        </th>
                        <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                          Bank Balance
                        </th>
                        <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                          Cashbook Balance
                        </th>
                        <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                          Client Ledger Total
                        </th>
                        <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                          Status
                        </th>
                        <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Completed
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {reconciliationPage.content.map((recon) => (
                        <tr
                          key={recon.id}
                          className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                          data-testid={`reconciliation-row-${recon.id}`}
                        >
                          <td className="py-3 pr-4 font-medium text-slate-950 dark:text-slate-50">
                            {formatLocalDate(recon.periodEnd)}
                          </td>
                          <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                            {formatCurrency(recon.bankBalance, currency)}
                          </td>
                          <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                            {formatCurrency(recon.cashbookBalance, currency)}
                          </td>
                          <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                            {formatCurrency(recon.clientLedgerTotal, currency)}
                          </td>
                          <td className="py-3 pr-4">
                            {recon.isBalanced ? (
                              <Badge variant="success">
                                <CheckCircle2 className="mr-1 size-3" />
                                Balanced
                              </Badge>
                            ) : (
                              <Badge variant="warning">
                                <AlertCircle className="mr-1 size-3" />
                                Unbalanced
                              </Badge>
                            )}
                          </td>
                          <td className="py-3 text-slate-700 dark:text-slate-300">
                            {recon.completedAt
                              ? formatLocalDate(
                                  recon.completedAt.split("T")[0],
                                )
                              : "---"}
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
