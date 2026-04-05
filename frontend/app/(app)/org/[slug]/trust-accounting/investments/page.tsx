import { notFound } from "next/navigation";
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
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchInvestments } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { TrustInvestment, TrustInvestmentStatus } from "@/lib/types/trust";
import { InvestmentPageClient } from "./InvestmentPageClient";

// -- Badge variant mapping ------------------------------------------------

const STATUS_BADGE_VARIANT: Record<
  TrustInvestmentStatus,
  "neutral" | "success" | "warning"
> = {
  ACTIVE: "success",
  MATURED: "warning",
  WITHDRAWN: "neutral",
};

// -- Maturity helper ------------------------------------------------------

function isMaturing(maturityDate: string | null): boolean {
  if (!maturityDate) return false;
  const today = new Date();
  const maturity = new Date(maturityDate);
  const diffDays =
    (maturity.getTime() - today.getTime()) / (1000 * 60 * 60 * 24);
  return diffDays >= 0 && diffDays <= 30;
}

// -- Page -----------------------------------------------------------------

export default async function InvestmentsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug: _slug } = await params;

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

  const hasManageTrust =
    capData.isAdmin ||
    capData.isOwner ||
    capData.capabilities.includes("MANAGE_TRUST");

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

  // Fetch investments
  let investments: TrustInvestment[] = [];
  let fetchError = false;

  if (accountId) {
    try {
      investments = await fetchInvestments(accountId);
    } catch {
      fetchError = true;
    }
  }

  const currency = settings.defaultCurrency ?? "ZAR";

  return (
    <div className="space-y-8" data-testid="investments-page">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Investments
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Trust investment register and maturity tracking
          </p>
        </div>
        {hasManageTrust && accountId && (
          <InvestmentPageClient
            accountId={accountId}
            variant="place"
            currency={currency}
          />
        )}
      </div>

      {/* Error State */}
      {(fetchError || accountFetchError) && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Unable to load investment data. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Investments Table */}
      {accountId && !fetchError && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>Investment Register</CardTitle>
                <CardDescription>
                  {investments.length} investment
                  {investments.length !== 1 ? "s" : ""} found
                </CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            {investments.length === 0 ? (
              <div className="py-8 text-center" data-testid="empty-investments">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No investments yet. Place one to get started.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table
                  className="w-full text-sm"
                  data-testid="investments-table"
                >
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700">
                      <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                        Client
                      </th>
                      <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                        Institution
                      </th>
                      <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                        Principal
                      </th>
                      <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                        Interest Rate
                      </th>
                      <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                        Deposit Date
                      </th>
                      <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                        Maturity Date
                      </th>
                      <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                        Interest Earned
                      </th>
                      <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                        Status
                      </th>
                      {hasManageTrust && (
                        <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                          Actions
                        </th>
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {investments.map((inv) => {
                      const maturing =
                        inv.status === "ACTIVE" && isMaturing(inv.maturityDate);
                      return (
                        <tr
                          key={inv.id}
                          className={`border-b border-slate-100 last:border-0 dark:border-slate-800 ${
                            maturing
                              ? "bg-amber-50 dark:bg-amber-950/20"
                              : ""
                          }`}
                          data-testid={
                            maturing
                              ? "maturity-alert"
                              : `investment-row-${inv.id}`
                          }
                        >
                          <td className="py-3 pr-4 text-slate-950 dark:text-slate-50">
                            {inv.customerId.slice(0, 8)}...
                          </td>
                          <td className="py-3 pr-4 text-slate-950 dark:text-slate-50">
                            {inv.institution}
                          </td>
                          <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                            {formatCurrency(inv.principal, currency)}
                          </td>
                          <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                            {(Number(inv.interestRate) * 100).toFixed(2)}%
                          </td>
                          <td className="py-3 pr-4 text-slate-950 dark:text-slate-50">
                            {formatLocalDate(inv.depositDate)}
                          </td>
                          <td className="py-3 pr-4 text-slate-950 dark:text-slate-50">
                            {inv.maturityDate
                              ? formatLocalDate(inv.maturityDate)
                              : "Call deposit"}
                          </td>
                          <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                            {formatCurrency(inv.interestEarned, currency)}
                          </td>
                          <td className="py-3 pr-4">
                            <Badge variant={STATUS_BADGE_VARIANT[inv.status]}>
                              {inv.status}
                            </Badge>
                          </td>
                          {hasManageTrust && (
                            <td className="py-3">
                              {inv.status === "ACTIVE" && (
                                <InvestmentPageClient
                                  accountId={accountId!}
                                  variant="actions"
                                  investmentId={inv.id}
                                  investmentPrincipal={inv.principal}
                                  investmentInterestEarned={inv.interestEarned}
                                  currency={currency}
                                />
                              )}
                            </td>
                          )}
                        </tr>
                      );
                    })}
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
