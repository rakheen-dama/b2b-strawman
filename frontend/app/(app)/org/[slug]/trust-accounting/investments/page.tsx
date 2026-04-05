import { notFound } from "next/navigation";
import { PiggyBank } from "lucide-react";
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
import { fetchInvestments, fetchMaturing } from "./actions";
import { InvestmentActions } from "@/components/trust/InvestmentActions";
import { formatLocalDate } from "@/lib/format";
import type { TrustInvestmentStatus } from "@/lib/types";

// ── Helpers ────────────────────────────────────────────────────────

function formatCurrency(amount: number, currency = "ZAR"): string {
  return new Intl.NumberFormat("en-ZA", {
    style: "currency",
    currency,
  }).format(amount);
}

function statusBadgeVariant(
  status: TrustInvestmentStatus,
): "success" | "warning" | "neutral" {
  switch (status) {
    case "ACTIVE":
      return "success";
    case "MATURED":
      return "warning";
    case "WITHDRAWN":
    default:
      return "neutral";
  }
}

function formatPercent(value: number): string {
  return `${(value * 100).toFixed(2)}%`;
}

// ── Page ───────────────────────────────────────────────────────────

export default async function InvestmentsPage({
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
            Investments
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Trust investment register
          </p>
        </div>
        <Card>
          <CardContent className="py-10 text-center">
            <PiggyBank className="mx-auto size-8 text-slate-400" />
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              No trust accounts found. Create a trust account first.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  const [investments, maturingInvestments] = await Promise.all([
    fetchInvestments(accountId).catch(() => []),
    fetchMaturing(accountId, 30).catch(() => []),
  ]);

  const maturingIds = new Set(maturingInvestments.map((i) => i.id));
  const currency = settings.defaultCurrency ?? "ZAR";

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Investments
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Trust investment register and management
          </p>
        </div>
        {canManageTrust && (
          <InvestmentActions accountId={accountId} slug={slug} />
        )}
      </div>

      {/* Maturity Alert */}
      {maturingInvestments.length > 0 && (
        <div
          className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-400"
          data-testid="maturity-alert"
        >
          {maturingInvestments.length} investment
          {maturingInvestments.length === 1 ? "" : "s"} maturing within 30
          days
        </div>
      )}

      {/* Investments Table */}
      <Card>
        <CardHeader>
          <CardTitle>Investment Register</CardTitle>
          <CardDescription>
            All trust investments with current status
          </CardDescription>
        </CardHeader>
        <CardContent>
          {investments.length === 0 ? (
            <div className="py-8 text-center">
              <PiggyBank className="mx-auto size-8 text-slate-400" />
              <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
                No investments yet. Place a new investment to get started.
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
                    {canManageTrust && (
                      <th className="pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Actions
                      </th>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {investments.map((inv) => (
                    <tr
                      key={inv.id}
                      className={`border-b border-slate-100 last:border-0 dark:border-slate-800 ${
                        maturingIds.has(inv.id)
                          ? "bg-amber-50 dark:bg-amber-950/30"
                          : ""
                      }`}
                    >
                      <td className="py-3 pr-4 font-medium text-slate-950 dark:text-slate-50">
                        {inv.customerName}
                      </td>
                      <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                        {inv.institution}
                      </td>
                      <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                        {formatCurrency(inv.principal, currency)}
                      </td>
                      <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                        {formatPercent(inv.interestRate)}
                      </td>
                      <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                        {formatLocalDate(inv.depositDate)}
                      </td>
                      <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                        {inv.maturityDate
                          ? formatLocalDate(inv.maturityDate)
                          : "\u2014"}
                      </td>
                      <td className="py-3 pr-4 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                        {formatCurrency(inv.interestEarned, currency)}
                      </td>
                      <td className="py-3 pr-4">
                        <div className="flex items-center gap-2">
                          <Badge variant={statusBadgeVariant(inv.status)}>
                            {inv.status}
                          </Badge>
                          {maturingIds.has(inv.id) &&
                            inv.status === "ACTIVE" && (
                              <Badge variant="warning">Maturing soon</Badge>
                            )}
                        </div>
                      </td>
                      {canManageTrust && (
                        <td className="py-3">
                          <InvestmentRowActions
                            investmentId={inv.id}
                            status={inv.status}
                            principal={inv.principal}
                            interestEarned={inv.interestEarned}
                          />
                        </td>
                      )}
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

// ── Inline row-action reference for server rendering ──────────────
// This delegates to the client component via data props (no functions passed)

function InvestmentRowActions({
  investmentId,
  status,
  principal,
  interestEarned,
}: {
  investmentId: string;
  status: TrustInvestmentStatus;
  principal: number;
  interestEarned: number;
}) {
  // Only render action buttons for ACTIVE investments
  if (status !== "ACTIVE") {
    return <span className="text-xs text-slate-400">&mdash;</span>;
  }

  return (
    <InvestmentActions
      investmentId={investmentId}
      principal={principal}
      interestEarned={interestEarned}
      variant="row"
    />
  );
}
