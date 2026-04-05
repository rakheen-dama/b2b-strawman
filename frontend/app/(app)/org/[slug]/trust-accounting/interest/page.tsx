import { notFound } from "next/navigation";
import { Landmark } from "lucide-react";
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
import { fetchInterestRuns, fetchLpffRates } from "./actions";
import { InterestActions } from "@/components/trust/InterestActions";
import { formatLocalDate } from "@/lib/format";
import type { InterestRunStatus } from "@/lib/types";

// ── Helpers ────────────────────────────────────────────────────────

function formatCurrency(amount: number, currency = "ZAR"): string {
  return new Intl.NumberFormat("en-ZA", {
    style: "currency",
    currency,
  }).format(amount);
}

function statusBadgeVariant(
  status: InterestRunStatus,
): "success" | "warning" | "neutral" {
  switch (status) {
    case "POSTED":
      return "success";
    case "APPROVED":
      return "warning";
    case "DRAFT":
    default:
      return "neutral";
  }
}

function formatPercent(value: number): string {
  return `${(value * 100).toFixed(2)}%`;
}

// ── Page ───────────────────────────────────────────────────────────

export default async function InterestPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  await params;

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
    // handle error
  }

  if (!accountId) {
    return (
      <div className="space-y-8">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Interest
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            LPFF interest calculation and allocation
          </p>
        </div>
        <Card>
          <CardContent className="py-10 text-center">
            <Landmark className="mx-auto size-8 text-slate-400" />
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              No trust accounts found. Create a trust account first.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  const [interestRuns, lpffRates] = await Promise.all([
    fetchInterestRuns(accountId).catch(() => []),
    fetchLpffRates(accountId).catch(() => []),
  ]);

  const currency = settings.defaultCurrency ?? "ZAR";

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Interest
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            LPFF interest calculation and allocation
          </p>
        </div>
        {canManageTrust && (
          <InterestActions accountId={accountId} />
        )}
      </div>

      {/* Interest Runs Table */}
      <Card>
        <CardHeader>
          <CardTitle>Interest Runs</CardTitle>
          <CardDescription>
            History of interest calculation and allocation runs
          </CardDescription>
        </CardHeader>
        <CardContent>
          {interestRuns.length === 0 ? (
            <div className="py-8 text-center">
              <Landmark className="mx-auto size-8 text-slate-400" />
              <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
                No interest runs yet. Create a new interest run to get started.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table
                className="w-full text-sm"
                data-testid="interest-runs-table"
              >
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-700">
                    <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                      Period
                    </th>
                    <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                      Total Interest
                    </th>
                    <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                      LPFF Share
                    </th>
                    <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                      Client Share
                    </th>
                    <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                      Status
                    </th>
                    <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                      Posted Date
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {interestRuns.map((run) => (
                    <tr
                      key={run.id}
                      className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                    >
                      <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                        {formatLocalDate(run.periodStart)} &ndash;{" "}
                        {formatLocalDate(run.periodEnd)}
                      </td>
                      <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                        {formatCurrency(run.totalInterest, currency)}
                      </td>
                      <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                        {formatCurrency(run.totalLpffShare, currency)}
                      </td>
                      <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                        {formatCurrency(run.totalClientShare, currency)}
                      </td>
                      <td className="py-3 pr-4">
                        <Badge variant={statusBadgeVariant(run.status)}>
                          {run.status}
                        </Badge>
                      </td>
                      <td className="py-3 text-slate-500 dark:text-slate-400">
                        {run.postedAt
                          ? formatLocalDate(run.postedAt.slice(0, 10))
                          : "\u2014"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* LPFF Rate History */}
      <Card>
        <CardHeader>
          <CardTitle>LPFF Rate History</CardTitle>
          <CardDescription>
            Lawyers Fidelity Fund rate configuration over time
          </CardDescription>
        </CardHeader>
        <CardContent>
          {lpffRates.length === 0 ? (
            <div className="py-8 text-center">
              <p className="text-sm text-slate-500 dark:text-slate-400">
                No LPFF rates configured yet.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table
                className="w-full text-sm"
                data-testid="lpff-rates-table"
              >
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-700">
                    <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                      Effective Date
                    </th>
                    <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                      Rate %
                    </th>
                    <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                      LPFF Share %
                    </th>
                    <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                      Notes
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {lpffRates.map((rate) => (
                    <tr
                      key={rate.id}
                      className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                    >
                      <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                        {formatLocalDate(rate.effectiveFrom)}
                      </td>
                      <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                        {formatPercent(rate.ratePercent)}
                      </td>
                      <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                        {formatPercent(rate.lpffSharePercent)}
                      </td>
                      <td className="py-3 text-slate-500 dark:text-slate-400">
                        {rate.notes ?? "\u2014"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
