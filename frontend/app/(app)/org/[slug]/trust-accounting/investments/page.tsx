import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { Badge } from "@b2mash/ui/badge";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchInvestments } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  InvestmentBasis,
  TrustAccount,
  TrustInvestment,
  TrustInvestmentStatus,
} from "@/lib/types/trust";
import { InvestmentPageClient } from "./InvestmentPageClient";
import { InvestmentBasisFilter } from "./InvestmentBasisFilter";
import { daysBetween, todayInSA } from "@b2mash/shared/date";

// -- Badge variant mapping ------------------------------------------------

const STATUS_BADGE_VARIANT: Record<TrustInvestmentStatus, "neutral" | "success" | "warning"> = {
  ACTIVE: "success",
  MATURED: "warning",
  WITHDRAWN: "neutral",
  CANCELLED: "neutral",
};

/**
 * Status text in the register (no raw enum on screen). CANCELLED: the placement was rejected, so
 * nothing was invested (G89-F87).
 */
const STATUS_LABEL: Record<TrustInvestmentStatus, string> = {
  ACTIVE: "Active",
  MATURED: "Matured",
  WITHDRAWN: "Withdrawn",
  CANCELLED: "Cancelled",
};

/** The backend withdraws a held investment — ACTIVE or MATURED (648A). */
const WITHDRAWABLE: ReadonlySet<TrustInvestmentStatus> = new Set(["ACTIVE", "MATURED"]);

/** A section 86(3) investment is the firm's surplus trust money; it names no client. */
const FIRM_CLIENT_LABEL = "Firm (section 86(3))";

const BASIS_BADGE_VARIANT: Record<InvestmentBasis, "neutral" | "lead"> = {
  FIRM_DISCRETION: "neutral",
  CLIENT_INSTRUCTION: "lead",
};

const BASIS_LABEL: Record<InvestmentBasis, string> = {
  FIRM_DISCRETION: "Section 86(3)",
  CLIENT_INSTRUCTION: "Section 86(4)",
};

// -- Register cell classes (W-1: every cell may wrap; secondary facts stack under the first) --

const HEAD = "pr-4 pb-3 text-left align-bottom font-medium text-slate-500 dark:text-slate-400";
const CELL = "py-3 pr-4 align-top text-slate-950 dark:text-slate-50";
const SUB = "block text-xs text-slate-500 dark:text-slate-400";
const MONEY = "text-right font-mono tabular-nums";

// -- Maturity helper ------------------------------------------------------

/** Matures within `daysAhead` days of South African today, inclusive (G88-F49). */
function isMaturing(maturityDate: string | null, daysAhead: number = 30): boolean {
  if (!maturityDate) return false;
  const diffDays = daysBetween(todayInSA(), maturityDate);
  return diffDays >= 0 && diffDays <= daysAhead;
}

// -- Page -----------------------------------------------------------------

export default async function InvestmentsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ investmentBasis?: string }>;
}) {
  const { slug: _slug } = await params;
  const { investmentBasis: rawBasis } = await searchParams;

  const VALID_BASES: InvestmentBasis[] = ["FIRM_DISCRETION", "CLIENT_INSTRUCTION"];
  const basisFilter =
    rawBasis && VALID_BASES.includes(rawBasis as InvestmentBasis)
      ? (rawBasis as InvestmentBasis)
      : undefined;

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

  // The funding (primary trust banking) account, and the investment accounts (648A)
  let accountId: string | null = null;
  let investmentAccounts: TrustAccount[] = [];
  let accountFetchError = false;
  try {
    const accounts = await fetchTrustAccounts();
    const banking = accounts.filter((a) => a.accountType !== "INVESTMENT");
    const primary = banking.find((a) => a.isPrimary) ?? banking[0];
    if (primary) {
      accountId = primary.id;
    }
    investmentAccounts = accounts.filter(
      (a) => a.accountType === "INVESTMENT" && a.status === "ACTIVE"
    );
  } catch {
    accountFetchError = true;
  }

  // Fetch investments
  let investments: TrustInvestment[] = [];
  let fetchError = false;

  if (accountId) {
    try {
      investments = await fetchInvestments(accountId, basisFilter);
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
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Investments</h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Trust investment register and maturity tracking
          </p>
        </div>
        {hasManageTrust && accountId && (
          <InvestmentPageClient
            accountId={accountId}
            variant="place"
            investmentAccounts={investmentAccounts}
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
              <InvestmentBasisFilter currentValue={basisFilter} />
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
              // W-1 (648A gate): eight columns that fit the card at 1280 without a horizontal
              // scroll — the basis sits under the client, the institution and account number under
              // the investment account, the LPFF share under the rate, the maturity under the
              // deposit date and the interest under the principal; text and Badges wrap.
              <div className="overflow-x-auto">
                <table className="w-full text-sm" data-testid="investments-table">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700">
                      <th className={HEAD}>Client</th>
                      <th className={HEAD}>Investment account</th>
                      <th className={HEAD}>Rate</th>
                      <th className={HEAD}>Term</th>
                      <th className={cn(HEAD, "text-right")}>Principal</th>
                      <th className={cn(HEAD, "text-right")}>Balance</th>
                      <th className={HEAD}>Status</th>
                      {hasManageTrust && <th className={cn(HEAD, "pr-0")}>Actions</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {investments.map((inv) => {
                      const maturing = inv.status === "ACTIVE" && isMaturing(inv.maturityDate);
                      return (
                        <tr
                          key={inv.id}
                          className={cn(
                            "border-b border-slate-100 last:border-0 dark:border-slate-800",
                            maturing && "bg-amber-50 dark:bg-amber-950/30"
                          )}
                          data-testid={maturing ? "maturity-alert" : `investment-row-${inv.id}`}
                        >
                          <td className={CELL} data-testid="investment-client-cell">
                            <span className="block">{inv.clientName ?? FIRM_CLIENT_LABEL}</span>
                            <Badge
                              variant={BASIS_BADGE_VARIANT[inv.investmentBasis]}
                              className="mt-1 shrink whitespace-normal"
                            >
                              {BASIS_LABEL[inv.investmentBasis]}
                            </Badge>
                          </td>
                          <td className={CELL} data-testid="investment-account-cell">
                            <span className="block">{inv.investmentAccountName}</span>
                            <span className={SUB}>{inv.institution}</span>
                            <span className={SUB}>{inv.accountNumber}</span>
                          </td>
                          <td className={cn(CELL, "tabular-nums")}>
                            <span className="block font-mono">
                              {(Number(inv.interestRate) * 100).toFixed(2)}%
                            </span>
                            <span className={SUB}>
                              LPFF share{" "}
                              {inv.investmentBasis === "CLIENT_INSTRUCTION" ? (
                                <span className="font-medium text-teal-600 dark:text-teal-400">
                                  5% (statutory)
                                </span>
                              ) : (
                                <span>Arrangement</span>
                              )}
                            </span>
                          </td>
                          <td className={CELL}>
                            <span className="block">{formatLocalDate(inv.depositDate)}</span>
                            <span className={SUB}>
                              {inv.maturityDate
                                ? `to ${formatLocalDate(inv.maturityDate)}`
                                : "Call deposit"}
                            </span>
                          </td>
                          <td className={cn(CELL, MONEY)}>
                            <span className="block">{formatCurrency(inv.principal, currency)}</span>
                            <span className={SUB}>
                              Interest {formatCurrency(inv.interestEarned, currency)}
                            </span>
                          </td>
                          <td className={cn(CELL, MONEY)}>
                            {formatCurrency(inv.balance, currency)}
                          </td>
                          <td className={CELL}>
                            {inv.awaitingApproval ? (
                              <Badge variant="warning" className="shrink whitespace-normal">
                                Awaiting approval
                              </Badge>
                            ) : (
                              <Badge
                                variant={STATUS_BADGE_VARIANT[inv.status]}
                                className="shrink whitespace-normal"
                              >
                                {STATUS_LABEL[inv.status]}
                              </Badge>
                            )}
                          </td>
                          {hasManageTrust && (
                            <td className={cn(CELL, "pr-0")}>
                              {WITHDRAWABLE.has(inv.status) && !inv.awaitingApproval && (
                                <InvestmentPageClient
                                  accountId={accountId!}
                                  variant="actions"
                                  investmentId={inv.id}
                                  investmentBalance={inv.balance}
                                  investmentDepositDate={inv.depositDate}
                                  canRecordInterest={inv.status === "ACTIVE"}
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
