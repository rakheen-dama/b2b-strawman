import { notFound } from "next/navigation";
import Link from "next/link";
import { FileCheck, Plus } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchReconciliations } from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { TrustReconciliation } from "@/lib/types";

function formatPeriod(periodEnd: string): string {
  const date = new Date(periodEnd + "T00:00:00Z");
  return date.toLocaleDateString("en-US", {
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}

export default async function ReconciliationPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

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

  // Fetch reconciliations
  let reconciliations: TrustReconciliation[] = [];
  let fetchError = false;

  if (accountId) {
    try {
      reconciliations = await fetchReconciliations(accountId);
    } catch {
      fetchError = true;
    }
  }

  const currency = settings.defaultCurrency ?? "ZAR";

  return (
    <div className="space-y-8" data-testid="reconciliation-list-page">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Reconciliations
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Monthly trust account reconciliations
          </p>
        </div>
        <Button asChild data-testid="new-reconciliation-btn">
          <Link href={`/org/${slug}/trust-accounting/reconciliation/new`}>
            <Plus className="mr-1.5 size-4" />
            New Reconciliation
          </Link>
        </Button>
      </div>

      {/* Error State */}
      {(fetchError || accountFetchError) && (
        <Card>
          <CardContent className="py-10 text-center">
            <p
              className="text-sm text-slate-500 dark:text-slate-400"
              data-testid="error-state"
            >
              Unable to load reconciliations. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {/* No Account State */}
      {!accountId && !accountFetchError && (
        <Card>
          <CardContent className="py-10 text-center">
            <FileCheck className="mx-auto mb-3 size-10 text-slate-300 dark:text-slate-600" />
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No trust account configured.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Reconciliation Table */}
      {accountId && !fetchError && !accountFetchError && (
        <Card>
          <CardHeader>
            <CardTitle>Reconciliation History</CardTitle>
            <CardDescription>
              {reconciliations.length} reconciliation
              {reconciliations.length !== 1 ? "s" : ""}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {reconciliations.length === 0 ? (
              <div className="py-8 text-center" data-testid="empty-state">
                <FileCheck className="mx-auto mb-3 size-10 text-slate-300 dark:text-slate-600" />
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No reconciliations yet. Start by creating a new
                  reconciliation.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table
                  className="w-full text-sm"
                  data-testid="reconciliation-table"
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
                      <th className="pb-3 pr-4 text-center font-medium text-slate-500 dark:text-slate-400">
                        Status
                      </th>
                      <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Completed
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {reconciliations.map((rec) => (
                      <tr
                        key={rec.id}
                        className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                      >
                        <td className="py-3 pr-4 font-medium text-slate-950 dark:text-slate-50">
                          {formatPeriod(rec.periodEnd)}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                          {formatCurrency(rec.bankBalance, currency)}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                          {formatCurrency(rec.cashbookBalance, currency)}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                          {formatCurrency(rec.clientLedgerTotal, currency)}
                        </td>
                        <td className="py-3 pr-4 text-center">
                          {rec.isBalanced ? (
                            <Badge variant="success">Balanced</Badge>
                          ) : (
                            <Badge variant="destructive">Unbalanced</Badge>
                          )}
                        </td>
                        <td className="py-3 text-slate-600 dark:text-slate-400">
                          {rec.completedAt
                            ? formatLocalDate(rec.completedAt.slice(0, 10))
                            : "—"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
