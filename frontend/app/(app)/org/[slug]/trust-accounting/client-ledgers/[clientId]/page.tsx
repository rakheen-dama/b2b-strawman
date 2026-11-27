import { notFound } from "next/navigation";
import Link from "next/link";
import { AlertCircle, ArrowLeft, Receipt } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { PageHeader } from "@b2mash/ui/page-header";
import { StatCard } from "@b2mash/ui/stat-card";
import {
  fetchTrustAccounts,
  fetchTrustTransactionTypes,
} from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchClientLedger,
  fetchClientHistory,
  type ClientHistoryPage,
} from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
import { EmptyState } from "@/components/empty-state";
import { PrintStatementButton } from "@/components/trust/print-statement-button";
import { ClientHistoryDataTable } from "@/components/trust/client-history-data-table";
import { ClientHistoryFilters } from "@/components/trust/client-history-filters";
import { LedgerTotalsBreakdown } from "@/components/trust/ledger-totals-breakdown";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import { searchParamsToQuery } from "@/lib/search-params-query";
import {
  offeredTransactionTypes,
  type TrustTransactionStatus,
  type TrustTransactionTypeInfo,
  type ClientLedgerCard,
} from "@/lib/types";

// ── Helpers ───────────────────────────────────────────────────────

const PILL_CLASS = "rounded-full px-3 py-1 text-sm transition-colors";
const PILL_ACTIVE = "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900";
const PILL_IDLE =
  "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700";

const STATUS_OPTIONS: TrustTransactionStatus[] = [
  "RECORDED",
  "AWAITING_APPROVAL",
  "APPROVED",
  "REJECTED",
  "REVERSED",
];

/**
 * The types a client ledger can hold (646E, D6): rows on the client's own ledger or its investment
 * ledger, plus Reversal (ledger null — it moves its original's). Non-client and inter-account
 * types never sit on a client ledger, so they are not offered as filters here. Take-on-only types
 * (657A) are removed first by `offeredTransactionTypes`, the one rule for what is offered.
 */
function isClientLedgerType(t: TrustTransactionTypeInfo): boolean {
  return t.ledger === "CLIENT" || t.ledger === "CLIENT_INVESTMENT" || t.code === "REVERSAL";
}

// ── Page ──────────────────────────────────────────────────────────

export default async function ClientDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string; clientId: string }>;
  searchParams: Promise<{
    status?: string;
    type?: string;
    dateFrom?: string;
    dateTo?: string;
    page?: string;
  }>;
}) {
  const { slug, clientId } = await params;
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

  // The type registry (646E) for the filter pills — static, so it is fetched concurrently with
  // the account, ledger and history fetches below. Non-fatal: without it only the pills go.
  const typesPromise: Promise<TrustTransactionTypeInfo[]> = (async () => {
    try {
      return offeredTransactionTypes(await fetchTrustTransactionTypes()).filter(isClientLedgerType);
    } catch {
      return [];
    }
  })();

  // Fetch primary trust account
  let accountId: string | null = null;
  try {
    const accounts = await fetchTrustAccounts();
    const primary = accounts.find((a) => a.isPrimary) ?? accounts[0];
    if (primary) {
      accountId = primary.id;
    }
  } catch {
    notFound();
  }

  if (!accountId) {
    notFound();
  }

  // Fetch client ledger card
  let ledgerCard: ClientLedgerCard | null = null;
  try {
    ledgerCard = await fetchClientLedger(accountId, clientId);
  } catch {
    notFound();
  }

  if (!ledgerCard) {
    notFound();
  }

  // Fetch transaction history
  let historyPage: ClientHistoryPage | null = null;
  let fetchError = false;
  try {
    historyPage = await fetchClientHistory(accountId, clientId, {
      status: search.status,
      type: search.type,
      dateFrom: search.dateFrom,
      dateTo: search.dateTo,
      page: search.page ? parseInt(search.page, 10) : 0,
      size: 20,
    });
  } catch {
    fetchError = true;
  }

  const types = await typesPromise;

  const currency = settings.defaultCurrency ?? "ZAR";
  // 648E: held for the client in section 86(4) investments placed from this account
  const invested = ledgerCard.investedBalance;
  const basePath = `/org/${slug}/trust-accounting/client-ledgers/${clientId}`;
  // Every string param, in the URL's order: the pager keeps them all and sets `page` (DA-8).
  const query = searchParamsToQuery(search);

  // Build filter URL helper (filter links drop `page`)
  function filterUrl(overrides: Record<string, string | undefined>): string {
    const newParams = new URLSearchParams();
    const merged = { ...search, ...overrides };
    for (const [key, value] of Object.entries(merged)) {
      if (value && key !== "page") newParams.set(key, value);
    }
    const qs = newParams.toString();
    return `${basePath}${qs ? `?${qs}` : ""}`;
  }

  // Status pills, type pills and the date filters: the table's toolbar, or on their own above the
  // error when the read failed (a bad filter must stay fixable).
  const filters = (
    <div className="w-full space-y-3">
      <div className="flex flex-wrap items-center gap-3" data-testid="status-filters">
        <span className="text-muted-foreground text-sm font-medium">Status:</span>
        <Link
          href={filterUrl({ status: undefined })}
          className={cn(PILL_CLASS, !search.status ? PILL_ACTIVE : PILL_IDLE)}
        >
          All
        </Link>
        {STATUS_OPTIONS.map((status) => (
          <Link
            key={status}
            href={filterUrl({ status })}
            className={cn(PILL_CLASS, search.status === status ? PILL_ACTIVE : PILL_IDLE)}
          >
            {status === "AWAITING_APPROVAL"
              ? "Awaiting Approval"
              : status.charAt(0) + status.slice(1).toLowerCase()}
          </Link>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-3" data-testid="type-filters">
        <span className="text-muted-foreground text-sm font-medium">Type:</span>
        <Link
          href={filterUrl({ type: undefined })}
          className={cn(PILL_CLASS, !search.type ? PILL_ACTIVE : PILL_IDLE)}
        >
          All
        </Link>
        {types.map((t) => (
          <Link
            key={t.code}
            href={filterUrl({ type: t.code })}
            className={cn(PILL_CLASS, search.type === t.code ? PILL_ACTIVE : PILL_IDLE)}
          >
            {t.label}
          </Link>
        ))}
      </div>

      {/* Date Range Filters */}
      <ClientHistoryFilters slug={slug} customerId={clientId} search={search} />
    </div>
  );

  return (
    <div className="space-y-8" data-testid="client-detail-page">
      {/* Back link + Header */}
      <div>
        <Link
          href={`/org/${slug}/trust-accounting/client-ledgers`}
          className="text-muted-foreground hover:text-foreground mb-4 inline-flex items-center gap-1 text-sm"
          data-testid="back-link"
        >
          <ArrowLeft className="size-4" />
          Back to Client Ledgers
        </Link>
        <PageHeader
          title={ledgerCard.clientName}
          description="Client ledger detail and transaction history"
          actions={<PrintStatementButton accountId={accountId} customerId={clientId} />}
        />
      </div>

      {/* Summary: the balance and the posted totals that make it up (G89-F13). Ledger read only —
          never computed from the history rows (X2). */}
      <div className="space-y-4" data-testid="client-summary">
        <StatCard
          label="Trust Balance"
          value={formatCurrency(ledgerCard.balance, currency)}
          className="sm:max-w-md"
          hint={
            // 648E (ADR-379 L7): money invested for the client stays theirs — show it beside the
            // general balance with the unchanged total; the breakdown carries the figures
            invested !== 0 ? (
              <span data-testid="client-summary-invested">
                Plus {formatCurrency(invested, currency)} invested (section 86(4)) —{" "}
                {formatCurrency(ledgerCard.balance + invested, currency)} held in trust in total
              </span>
            ) : undefined
          }
        />
        <div>
          <p className="text-muted-foreground mb-2 text-sm font-medium">Ledger totals</p>
          <LedgerTotalsBreakdown ledger={ledgerCard} currency={currency} layout="tiles" />
        </div>
      </div>

      {/* Error State: the filters stay usable */}
      {fetchError && (
        <>
          {filters}
          <Card>
            <CardContent>
              <EmptyState
                variant="compact"
                icon={AlertCircle}
                title="Unable to load transaction history. Please try again later."
                description={null}
              />
            </CardContent>
          </Card>
        </>
      )}

      {/* Transaction History Table */}
      {historyPage && (
        <Card>
          <CardHeader>
            <CardTitle>Transaction History</CardTitle>
            <CardDescription>
              {historyPage.totalElements} transaction
              {historyPage.totalElements !== 1 ? "s" : ""} found
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ClientHistoryDataTable
              rows={historyPage.content}
              currency={currency}
              pageIndex={historyPage.pageNumber}
              pageCount={historyPage.totalPages}
              basePath={basePath}
              query={query}
              toolbar={{ filters }}
              empty={
                <EmptyState
                  variant="inline"
                  icon={Receipt}
                  title="No transactions match the current filters"
                  description={null}
                />
              }
            />
          </CardContent>
        </Card>
      )}
    </div>
  );
}
