import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchInterestRuns,
  fetchLpffRates,
} from "@/app/(app)/org/[slug]/trust-accounting/interest/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { InterestRun, InterestRunStatus, LpffRate } from "@/lib/types/trust";
import { InterestPageClient } from "./InterestPageClient";

// ── Badge variant mapping ──────────────────────────────────────────

const STATUS_BADGE_VARIANT: Record<InterestRunStatus, "neutral" | "warning" | "success"> = {
  DRAFT: "neutral",
  APPROVED: "warning",
  POSTED: "success",
};

// ── Page ────────────────────────────────────────────────────────────

export default async function InterestPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug: _slug } = await params; // destructured for consistency with sibling pages

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

  const hasManageTrust =
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

  // Fetch interest runs + LPFF rates
  let runs: InterestRun[] = [];
  let rates: LpffRate[] = [];
  let fetchError = false;

  if (accountId) {
    try {
      [runs, rates] = await Promise.all([fetchInterestRuns(accountId), fetchLpffRates(accountId)]);
    } catch {
      fetchError = true;
    }
  }

  const currency = settings.defaultCurrency ?? "ZAR";

  return (
    <div className="space-y-8" data-testid="interest-page">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Interest</h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Interest runs and LPFF rate management
          </p>
        </div>
      </div>

      {/* Error State */}
      {(fetchError || accountFetchError) && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Unable to load interest data. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Interest Runs Table */}
      {accountId && !fetchError && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>Interest Runs</CardTitle>
                <CardDescription>
                  {runs.length} run{runs.length !== 1 ? "s" : ""} found
                </CardDescription>
              </div>
              {hasManageTrust && accountId && (
                <InterestPageClient
                  accountId={accountId}
                  variant="wizard"
                  canApprove={canApproveTrust}
                  currency={currency}
                />
              )}
            </div>
          </CardHeader>
          <CardContent>
            {runs.length === 0 ? (
              <div className="py-8 text-center" data-testid="empty-runs">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No interest runs yet. Create one to get started.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm" data-testid="interest-runs-table">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700">
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Period
                      </th>
                      <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        Total Interest
                      </th>
                      <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        LPFF Share
                      </th>
                      <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        Client Share
                      </th>
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Status
                      </th>
                      <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Posted Date
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {runs.map((run) => (
                      <tr
                        key={run.id}
                        className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                        data-testid={`run-row-${run.id}`}
                      >
                        <td className="py-3 pr-4 text-slate-950 dark:text-slate-50">
                          {formatLocalDate(run.periodStart)} — {formatLocalDate(run.periodEnd)}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono text-slate-950 tabular-nums dark:text-slate-50">
                          {formatCurrency(run.totalInterest, currency)}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono text-slate-950 tabular-nums dark:text-slate-50">
                          {formatCurrency(run.totalLpffShare, currency)}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono text-slate-950 tabular-nums dark:text-slate-50">
                          {formatCurrency(run.totalClientShare, currency)}
                        </td>
                        <td className="py-3 pr-4">
                          <Badge variant={STATUS_BADGE_VARIANT[run.status]}>{run.status}</Badge>
                        </td>
                        <td className="py-3 text-slate-600 dark:text-slate-400">
                          {run.postedAt ? formatLocalDate(run.postedAt.slice(0, 10)) : "—"}
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

      {/* LPFF Rate History */}
      {accountId && !fetchError && (
        <Card data-testid="lpff-rates-section">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>LPFF Rate History</CardTitle>
                <CardDescription>
                  {rates.length} rate{rates.length !== 1 ? "s" : ""} configured
                </CardDescription>
              </div>
              {hasManageTrust && accountId && (
                <InterestPageClient accountId={accountId} variant="rate" />
              )}
            </div>
          </CardHeader>
          <CardContent>
            {rates.length === 0 ? (
              <div className="py-8 text-center" data-testid="empty-rates">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No LPFF rates configured. Add a rate to enable interest calculations.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm" data-testid="lpff-rates-table">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700">
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Effective From
                      </th>
                      <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        Rate %
                      </th>
                      <th className="pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        LPFF Share %
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {rates.map((rate) => (
                      <tr
                        key={rate.id}
                        className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                        data-testid={`rate-row-${rate.id}`}
                      >
                        <td className="py-3 pr-4 text-slate-950 dark:text-slate-50">
                          {formatLocalDate(rate.effectiveFrom)}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono text-slate-950 tabular-nums dark:text-slate-50">
                          {(Number(rate.ratePercent) * 100).toFixed(2)}%
                        </td>
                        <td className="py-3 text-right font-mono text-slate-950 tabular-nums dark:text-slate-50">
                          {(Number(rate.lpffSharePercent) * 100).toFixed(2)}%
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
