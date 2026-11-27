import { notFound } from "next/navigation";
import Link from "next/link";
import { AlertCircle, Landmark, Receipt } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { listClients } from "@/lib/api/clients";
import { getCurrentUserEmail } from "@/lib/auth";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { PageHeader } from "@b2mash/ui/page-header";
import {
  fetchTrustAccounts,
  fetchTrustTransactionTypes,
} from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchTransactions,
  type TransactionPage,
} from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { EmptyState } from "@/components/empty-state";
import { TransactionActions } from "@/components/trust/transaction-actions";
import { TransactionFilters } from "@/components/trust/transaction-filters";
import { TransactionsDataTable } from "@/components/trust/transactions-data-table";
import { cn } from "@/lib/utils";
import {
  offeredTransactionTypes,
  type OrgMember,
  type TrustTransactionStatus,
  type TrustTransactionTypeInfo,
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

// ── Page ──────────────────────────────────────────────────────────

export default async function TransactionsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{
    status?: string;
    type?: string;
    dateFrom?: string;
    dateTo?: string;
    customerId?: string;
    projectId?: string;
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
  const canApproveTrust =
    capData.isAdmin || capData.isOwner || capData.capabilities.includes("APPROVE_TRUST_PAYMENT");

  // The type registry (646E): filter pills and reversal rules. Static, so it is fetched
  // concurrently with the account and transaction fetches below. Non-fatal — without it the rows
  // still render (labels and direction arrive on each row), only the pills and Reverse go.
  const typesPromise: Promise<TrustTransactionTypeInfo[]> = (async () => {
    try {
      return await fetchTrustTransactionTypes();
    } catch {
      return [];
    }
  })();

  // Fetch primary trust account
  let accountId: string | null = null;
  // 650D: the account's latest locked list period end — the posting dialogs warn on or before it
  let lockedThrough: string | null = null;
  let accountFetchError = false;
  try {
    const accounts = await fetchTrustAccounts();
    // A trust banking account, never an investment account (648A): placements are requested and
    // approved on the account the money leaves — the same rule as the investments page
    const banking = accounts.filter((a) => a.accountType !== "INVESTMENT");
    const primary = banking.find((a) => a.isPrimary) ?? banking[0];
    if (primary) {
      accountId = primary.id;
      lockedThrough = primary.lockedThrough ?? null;
    }
  } catch {
    accountFetchError = true;
  }

  // Fetch transactions
  let transactionPage: TransactionPage | null = null;
  let fetchError = false;

  if (accountId) {
    try {
      transactionPage = await fetchTransactions(accountId, {
        dateFrom: search.dateFrom,
        dateTo: search.dateTo,
        type: search.type,
        status: search.status,
        customerId: search.customerId,
        projectId: search.projectId,
        page: search.page ? parseInt(search.page, 10) : 0,
        size: 20,
      });
    } catch {
      fetchError = true;
    }
  }

  const types = await typesPromise;

  // Fetch the customer roster for the picker dialogs (Deposit/Payment/
  // Refund). Trimmed to {id, name, email} so the prop crosses the
  // server→client boundary cheaply.
  let pickerCustomers: Array<{ id: string; name: string; email: string }> = [];
  if (canManageTrust) {
    try {
      const customers = await listClients({ status: "ACTIVE" });
      pickerCustomers = customers.map((c) => ({
        id: c.id,
        name: c.name,
        email: c.email ?? "",
      }));
    } catch {
      // Non-fatal — pickers will render with an empty list and the dialog
      // surface its own "No clients found." empty state.
      pickerCustomers = [];
    }
  }

  // LZKC-016: resolve the viewer's member id so the Approve button can be
  // disabled for the member who already gave the first dual-mode approval.
  // Non-fatal — without it the backend still rejects duplicate approvers.
  let currentMemberId = "";
  if (canApproveTrust) {
    try {
      const [email, orgMembers] = await Promise.all([
        getCurrentUserEmail(),
        api.get<OrgMember[]>("/api/members"),
      ]);
      if (email) {
        const match = orgMembers.find((m) => m.email?.toLowerCase() === email.toLowerCase());
        if (match) currentMemberId = match.id;
      }
    } catch {
      // Non-fatal — approval progress still renders; server enforces approver rules.
    }
  }

  const currency = settings.defaultCurrency ?? "ZAR";
  const basePath = `/org/${slug}/trust-accounting/transactions`;
  // Every string param, in the URL's order: the pager keeps them all and sets `page` (DA-8).
  const query = Object.fromEntries(
    Object.entries(search).filter(
      (entry): entry is [string, string] => typeof entry[1] === "string" && entry[1] !== ""
    )
  );

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

  // Status pills, type pills and the date / client / matter filters: the table's toolbar, or on
  // their own above the error when the read failed (a bad filter must stay fixable).
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
        {/* 657A: take-on-only types are never offered; `types` stays whole for reversal rules */}
        {offeredTransactionTypes(types).map((t) => (
          <Link
            key={t.code}
            href={filterUrl({ type: t.code })}
            className={cn(PILL_CLASS, search.type === t.code ? PILL_ACTIVE : PILL_IDLE)}
          >
            {t.label}
          </Link>
        ))}
      </div>

      {/* Date Range, Client & Matter Filters */}
      <TransactionFilters slug={slug} search={search} />
    </div>
  );

  return (
    <div className="space-y-8">
      <PageHeader
        title="Transactions"
        description="Trust account transaction history"
        actions={
          canManageTrust && accountId ? (
            <TransactionActions
              accountId={accountId}
              slug={slug}
              customers={pickerCustomers}
              lockedThrough={lockedThrough}
            />
          ) : undefined
        }
      />

      {/* Error State: the filters stay usable */}
      {fetchError && (
        <>
          {filters}
          <Card>
            <CardContent>
              <EmptyState
                variant="compact"
                icon={AlertCircle}
                title="Unable to load transactions. Please try again later."
                description={null}
              />
            </CardContent>
          </Card>
        </>
      )}

      {/* Account Fetch Error State */}
      {accountFetchError && (
        <Card>
          <CardContent>
            <EmptyState
              variant="compact"
              icon={AlertCircle}
              title="Unable to load trust accounts. Please try again later."
              description={null}
            />
          </CardContent>
        </Card>
      )}

      {/* No Account State */}
      {!fetchError && !accountFetchError && !accountId && (
        <Card>
          <CardContent>
            <EmptyState
              variant="compact"
              icon={Landmark}
              title="No trust accounts have been set up yet."
              description={null}
            />
          </CardContent>
        </Card>
      )}

      {/* Transactions Table */}
      {transactionPage && (
        <Card>
          <CardHeader>
            <CardTitle>Transaction History</CardTitle>
            <CardDescription>
              {transactionPage.totalElements} transaction
              {transactionPage.totalElements !== 1 ? "s" : ""} found
            </CardDescription>
          </CardHeader>
          <CardContent>
            <TransactionsDataTable
              rows={transactionPage.content}
              currency={currency}
              pageIndex={transactionPage.pageNumber}
              pageCount={transactionPage.totalPages}
              basePath={basePath}
              query={query}
              types={types}
              canApproveTrust={canApproveTrust}
              canManageTrust={canManageTrust}
              currentMemberId={currentMemberId}
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
