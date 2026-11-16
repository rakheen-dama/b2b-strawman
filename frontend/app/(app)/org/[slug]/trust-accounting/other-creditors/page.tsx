import { notFound } from "next/navigation";
import { HandCoins, Inbox, Landmark } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardContent } from "@b2mash/ui/card";
import { PageHeader } from "@b2mash/ui/page-header";
import { StatCard } from "@b2mash/ui/stat-card";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchCreditorAccounts } from "@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions";
import { formatCurrency } from "@/lib/format";
import type { TrustCreditorAccount } from "@/lib/types";

// ── Page ──────────────────────────────────────────────────────────

/**
 * "Other trust creditors" (Phase 92 646B, ADR-376): the trust account's non-client creditor
 * ledgers — the Legal Practitioners' Fidelity Fund's interest and charges, and deposits whose owner
 * is not yet known — with their balances as at today. Rows and items arrive in 647A / 647B.
 */
export default async function OtherTrustCreditorsPage({
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

  // Fetch the non-client creditor ledgers
  let creditorAccounts: TrustCreditorAccount[] | null = null;
  let fetchError = false;
  if (accountId) {
    try {
      creditorAccounts = await fetchCreditorAccounts(accountId);
    } catch {
      fetchError = true;
    }
  }

  const currency = settings.defaultCurrency ?? "ZAR";

  return (
    <div className="space-y-8" data-testid="other-creditors-page">
      <PageHeader
        title="Other trust creditors"
        description="Trust money held for creditors who are not clients: the Legal Practitioners' Fidelity Fund's interest and charges, and deposits not yet identified"
      />

      {/* No Account State */}
      {!accountId && !accountFetchError && (
        <Card>
          <CardContent className="py-10 text-center">
            <HandCoins className="mx-auto mb-3 size-10 text-slate-300 dark:text-slate-600" />
            <p
              className="text-sm text-slate-500 dark:text-slate-400"
              data-testid="no-account-state"
            >
              No trust account configured. Set up a trust account to view other trust creditors.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Error State */}
      {(fetchError || accountFetchError) && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400" data-testid="error-state">
              Unable to load other trust creditors. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Creditor ledgers */}
      {creditorAccounts && (
        <div className="@container">
          <div
            className="grid grid-cols-1 gap-4 @xl:grid-cols-2"
            data-testid="creditor-account-cards"
          >
            {creditorAccounts.map((account) => (
              <StatCard
                key={account.id}
                label={account.name}
                icon={
                  account.accountType === "FUND_INTEREST_AND_CHARGES" ? <Landmark /> : <Inbox />
                }
                value={formatCurrency(account.balance, currency)}
                hint={<CreditorHint account={account} currency={currency} />}
                data-testid={`creditor-${account.accountType}`}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ── Hint ──────────────────────────────────────────────────────────

function CreditorHint({ account, currency }: { account: TrustCreditorAccount; currency: string }) {
  if (account.accountType === "FUND_INTEREST_AND_CHARGES") {
    if (account.inDebit) {
      return (
        <span role="status" className="text-warning-foreground" data-testid="fund-ledger-in-debit">
          The Fund interest and charges ledger is{" "}
          {formatCurrency(Math.abs(account.balance), currency)} in debit.
        </span>
      );
    }
    return <>Interest received for the Fund, less bank charges</>;
  }
  return <>Deposits whose owner is not yet known</>;
}
