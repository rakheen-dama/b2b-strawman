import { notFound } from "next/navigation";
import Link from "next/link";
import { AlertCircle, History, Landmark, Plus } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { Button } from "@b2mash/ui/button";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchBankStatements,
  fetchReconciliations,
  type ReconciliationPage,
} from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import { EmptyState } from "@/components/empty-state";
import {
  OpenStatementsDataTable,
  ReconciliationsDataTable,
  type OpenStatementRow,
} from "@/components/trust/reconciliations-data-table";
import type { TrustAccount } from "@/lib/types";

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
    capData.isAdmin || capData.isOwner || capData.capabilities.includes("VIEW_TRUST");
  if (!hasViewTrust) {
    notFound();
  }

  const canManageTrust =
    capData.isAdmin || capData.isOwner || capData.capabilities.includes("MANAGE_TRUST");

  // Fetch primary trust account
  let accountId: string | null = null;
  let accountFetchError = false;
  let accounts: TrustAccount[] = [];
  try {
    accounts = await fetchTrustAccounts();
    const primary = accounts.find((a) => a.isPrimary) ?? accounts[0];
    if (primary) {
      accountId = primary.id;
    }
  } catch {
    accountFetchError = true;
  }

  // Fetch reconciliations
  let reconciliationPage: ReconciliationPage | null = null;
  let fetchError = false;

  if (accountId) {
    try {
      const requestedPage = search.page && /^\d+$/.test(search.page) ? Number(search.page) : 0;
      reconciliationPage = await fetchReconciliations(accountId, {
        page: requestedPage,
        size: 20,
      });
    } catch {
      fetchError = true;
    }
  }

  // FU-6b: the imported statements not yet reconciled (replaced and discarded statements are
  // retired and not listed). One left behind by a refused wizard can be discarded here. Every trust
  // account the wizard offers (its account select reads the same fetchTrustAccounts list), so a
  // statement imported into a non-primary account can be discarded too (CodeRabbit round 1).
  let openStatements: OpenStatementRow[] | null = null;
  let statementsFetchError = false;
  if (accounts.length > 0) {
    try {
      const perAccount = await Promise.all(
        accounts.map(async (account) =>
          (await fetchBankStatements(account.id))
            .filter((s) => s.status !== "RECONCILED")
            .map((statement) => ({ statement, accountName: account.accountName }))
        )
      );
      openStatements = perAccount.flat();
    } catch {
      statementsFetchError = true;
    }
  }

  const currency = settings.defaultCurrency ?? "ZAR";
  const basePath = `/org/${slug}/trust-accounting/reconciliation`;

  return (
    <div className="space-y-8">
      <PageHeader
        title="Reconciliation"
        description="Trust account bank reconciliation history"
        actions={
          canManageTrust && accountId ? (
            <Button asChild>
              <Link href={`${basePath}/new`} data-testid="new-reconciliation-btn">
                <Plus className="mr-2 size-4" />
                New Reconciliation
              </Link>
            </Button>
          ) : undefined
        }
      />

      {/* Error states */}
      {accountFetchError && (
        <Card>
          <CardContent>
            <EmptyState
              variant="compact"
              icon={AlertCircle}
              title="Failed to load trust accounts. Please try again."
              description={null}
            />
          </CardContent>
        </Card>
      )}

      {!accountId && !accountFetchError && (
        <Card>
          <CardContent>
            <EmptyState
              variant="compact"
              icon={Landmark}
              title="No trust account found. Create a trust account first."
              description={null}
            />
          </CardContent>
        </Card>
      )}

      {fetchError && (
        <Card>
          <CardContent>
            <EmptyState
              variant="compact"
              icon={AlertCircle}
              title="Failed to load reconciliations. Please try again."
              description={null}
            />
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
            <ReconciliationsDataTable
              rows={reconciliationPage.content}
              currency={currency}
              pageIndex={reconciliationPage.pageNumber}
              pageCount={reconciliationPage.totalPages}
              basePath={basePath}
              empty={
                <EmptyState
                  variant="inline"
                  icon={History}
                  title="No reconciliations yet. Start a new reconciliation to begin."
                  description={null}
                />
              }
            />
          </CardContent>
        </Card>
      )}

      {/* FU-6b: imported statements not yet reconciled, with Discard */}
      {statementsFetchError && (
        <Card>
          <CardContent>
            <EmptyState
              variant="compact"
              icon={AlertCircle}
              title="Failed to load bank statements. Please try again."
              description={null}
            />
          </CardContent>
        </Card>
      )}

      {openStatements && openStatements.length > 0 && (
        <Card data-testid="open-statements">
          <CardHeader>
            <CardTitle>Statements Not Yet Reconciled</CardTitle>
            <CardDescription>
              Imported bank statements no reconciliation has completed. Discard one uploaded in
              error; to correct one, upload its replacement in a new reconciliation.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <OpenStatementsDataTable rows={openStatements} canManageTrust={canManageTrust} />
          </CardContent>
        </Card>
      )}
    </div>
  );
}
