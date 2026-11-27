import { notFound } from "next/navigation";
import { AlertCircle, PiggyBank } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchInvestments } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";
import { EmptyState } from "@/components/empty-state";
import { InvestmentsDataTable } from "@/components/trust/investments-data-table";
import type { InvestmentBasis, TrustAccount, TrustInvestment } from "@/lib/types/trust";
import { InvestmentPageClient } from "./InvestmentPageClient";
import { InvestmentBasisFilter } from "./InvestmentBasisFilter";
import { todayInSA } from "@b2mash/shared/date";

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
      <PageHeader
        title="Investments"
        description="Trust investment register and maturity tracking"
        actions={
          hasManageTrust && accountId ? (
            <InvestmentPageClient
              accountId={accountId}
              variant="place"
              investmentAccounts={investmentAccounts}
              currency={currency}
            />
          ) : undefined
        }
      />

      {/* Error State */}
      {(fetchError || accountFetchError) && (
        <Card>
          <CardContent>
            <EmptyState
              variant="compact"
              icon={AlertCircle}
              title="Unable to load investment data. Please try again later."
              description={null}
            />
          </CardContent>
        </Card>
      )}

      {/* Investments Table */}
      {accountId && !fetchError && (
        <Card>
          <CardHeader>
            <CardTitle>Investment Register</CardTitle>
            <CardDescription>
              {investments.length} investment
              {investments.length !== 1 ? "s" : ""} found
            </CardDescription>
          </CardHeader>
          <CardContent>
            <InvestmentsDataTable
              rows={investments}
              currency={currency}
              accountId={accountId}
              canManageTrust={hasManageTrust}
              todayIso={todayInSA()}
              toolbar={{ filters: <InvestmentBasisFilter currentValue={basisFilter} /> }}
              empty={
                <div data-testid="empty-investments">
                  <EmptyState
                    variant="inline"
                    icon={PiggyBank}
                    title="No investments yet. Place one to get started."
                    description={null}
                  />
                </div>
              }
            />
          </CardContent>
        </Card>
      )}
    </div>
  );
}
