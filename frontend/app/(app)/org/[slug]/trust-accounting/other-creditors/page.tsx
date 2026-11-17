import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft, ChevronRight, HandCoins, Inbox, Landmark } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { listClients } from "@/lib/api/clients";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@b2mash/ui/card";
import { PageHeader } from "@b2mash/ui/page-header";
import { StatCard } from "@b2mash/ui/stat-card";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchCreditorAccounts,
  fetchCreditorLedgerRows,
  fetchUnidentifiedDeposits,
} from "@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions";
import { FundLedgerTable } from "@/components/trust/FundLedgerTable";
import { UnidentifiedDepositsTable } from "@/components/trust/UnidentifiedDepositsTable";
import type { TrustPickerCustomer } from "@/components/trust/TrustEntityPickers";
import { formatCurrency } from "@/lib/format";
import type {
  CreditorLedgerPage,
  TrustCreditorAccount,
  UnidentifiedDepositPage,
} from "@/lib/types";

const FUND_LEDGER_PAGE_SIZE = 25;
const UNIDENTIFIED_PAGE_SIZE = 25;

// ── Page ──────────────────────────────────────────────────────────

/**
 * "Other trust creditors" (Phase 92 646B, ADR-376): the trust account's non-client creditor
 * ledgers — the Legal Practitioners' Fidelity Fund's interest and charges, and deposits whose owner
 * is not yet known — with their balances as at today. Under the balances, the Fund ledger's rows
 * with their running balances (647C), paged by `?page=`, and the unidentified deposits as items,
 * oldest first with their age and status (647B), paged by `?itemsPage=`. Each list has its own
 * fetch and error state, so one failing never hides the balances or the other list. With
 * MANAGE_TRUST a held item can be allocated to a client or paid to the Fund (647D; approval on the
 * transactions page), so the page also fetches the active client roster for the picker.
 */
export default async function OtherTrustCreditorsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ page?: string; itemsPage?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;
  const pageNumber = Math.max(0, Number.parseInt(search.page ?? "0", 10) || 0);
  const itemsPageNumber = Math.max(0, Number.parseInt(search.itemsPage ?? "0", 10) || 0);

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

  // The Fund ledger's rows: a failure here must not hide the balance cards (its own flag).
  const fund = creditorAccounts?.find((a) => a.accountType === "FUND_INTEREST_AND_CHARGES");
  let fundRows: CreditorLedgerPage | null = null;
  let fundRowsError = false;
  if (fund) {
    try {
      fundRows = await fetchCreditorLedgerRows(fund.id, {
        page: pageNumber,
        size: FUND_LEDGER_PAGE_SIZE,
      });
    } catch {
      fundRowsError = true;
    }
  }

  // The unidentified deposits (647B): their own flag, like the Fund ledger's rows.
  let items: UnidentifiedDepositPage | null = null;
  let itemsError = false;
  if (accountId && creditorAccounts) {
    try {
      items = await fetchUnidentifiedDeposits(accountId, {
        page: itemsPageNumber,
        size: UNIDENTIFIED_PAGE_SIZE,
      });
    } catch {
      itemsError = true;
    }
  }

  // The client roster for the allocation picker (647D): only for MANAGE_TRUST, trimmed to
  // {id, name, email} for the server → client boundary. Non-fatal: the picker shows its own empty
  // state.
  let pickerCustomers: TrustPickerCustomer[] = [];
  if (canManageTrust && items && items.content.some((item) => item.status === "HELD")) {
    try {
      const customers = await listClients({ status: "ACTIVE" });
      pickerCustomers = customers.map((c) => ({ id: c.id, name: c.name, email: c.email ?? "" }));
    } catch {
      pickerCustomers = [];
    }
  }

  const currency = settings.defaultCurrency ?? "ZAR";
  // Each list pages on its own parameter (`?page=` the Fund ledger, `?itemsPage=` the items) and
  // keeps the other's place.
  const listUrl = (page: number, itemsPage: number) =>
    `/org/${slug}/trust-accounting/other-creditors?page=${page}` +
    (itemsPage > 0 ? `&itemsPage=${itemsPage}` : "");
  const pageUrl = (page: number) => listUrl(page, itemsPageNumber);
  const itemsPageUrl = (itemsPage: number) => listUrl(pageNumber, itemsPage);

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

      {/* The Fund ledger's rows (647C) */}
      {fund && (
        <Card data-testid="fund-ledger-section">
          <CardHeader>
            <CardTitle>{fund.name}</CardTitle>
            <CardDescription>
              Interest received, transfers to the Fund, bank charges and refunds, newest first, with
              the running balance
            </CardDescription>
          </CardHeader>
          <CardContent>
            {fundRowsError && (
              <p
                role="alert"
                className="text-sm text-slate-500 dark:text-slate-400"
                data-testid="fund-ledger-error"
              >
                Unable to load the Fund ledger&apos;s entries. Please try again later.
              </p>
            )}
            {fundRows && (
              <>
                <FundLedgerTable
                  rows={fundRows.content}
                  currency={currency}
                  empty={
                    // A `?page=` past the last page is not an empty ledger: say so and link back.
                    pageNumber > 0 ? (
                      <p
                        className="py-6 text-center text-sm text-slate-500 dark:text-slate-400"
                        data-testid="fund-ledger-page-out-of-range"
                      >
                        No entries on this page.{" "}
                        <Link
                          href={pageUrl(0)}
                          className="text-foreground font-medium underline underline-offset-4"
                        >
                          Go to the first page
                        </Link>
                      </p>
                    ) : (
                      <p
                        className="py-6 text-center text-sm text-slate-500 dark:text-slate-400"
                        data-testid="fund-ledger-empty"
                      >
                        No entries on the Fund ledger yet. Record interest, transfers and bank
                        charges from the bank statement when you reconcile.
                      </p>
                    )
                  }
                />
                <LedgerPager page={fundRows.page} hrefFor={pageUrl} />
              </>
            )}
          </CardContent>
        </Card>
      )}

      {/* The unidentified deposits, as items (647B) */}
      {creditorAccounts && (
        <Card data-testid="unidentified-deposits-section">
          <CardHeader>
            <CardTitle>Unidentified deposits</CardTitle>
            <CardDescription>
              Bank credits whose owner is not yet known, oldest first, with their age
            </CardDescription>
          </CardHeader>
          <CardContent>
            {itemsError && (
              <p
                role="alert"
                className="text-sm text-slate-500 dark:text-slate-400"
                data-testid="unidentified-deposits-error"
              >
                Unable to load the unidentified deposits. Please try again later.
              </p>
            )}
            {items && (
              <>
                <UnidentifiedDepositsTable
                  items={items.content}
                  currency={currency}
                  canManage={canManageTrust}
                  customers={pickerCustomers}
                  empty={
                    itemsPageNumber > 0 ? (
                      <p
                        className="py-6 text-center text-sm text-slate-500 dark:text-slate-400"
                        data-testid="unidentified-deposits-page-out-of-range"
                      >
                        No unidentified deposits on this page.{" "}
                        <Link
                          href={itemsPageUrl(0)}
                          className="text-foreground font-medium underline underline-offset-4"
                        >
                          Go to the first page
                        </Link>
                      </p>
                    ) : (
                      <p
                        className="py-6 text-center text-sm text-slate-500 dark:text-slate-400"
                        data-testid="unidentified-deposits-empty"
                      >
                        No unidentified deposits. Record an unknown credit from the bank statement
                        when you reconcile.
                      </p>
                    )
                  }
                />
                <LedgerPager page={items.page} hrefFor={itemsPageUrl} />
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}

// ── Pager ─────────────────────────────────────────────────────────

/** Previous / next links for one server-paged list; nothing when it fits on one page. */
function LedgerPager({
  page,
  hrefFor,
}: {
  page: { number: number; totalPages: number };
  hrefFor: (page: number) => string;
}) {
  if (page.totalPages <= 1) {
    return null;
  }
  return (
    <div className="mt-4 flex items-center justify-between border-t border-slate-200 pt-4 dark:border-slate-700">
      <p className="text-sm text-slate-500 dark:text-slate-400">
        Page {page.number + 1} of {page.totalPages}
      </p>
      <div className="flex items-center gap-2">
        {page.number > 0 ? (
          <Button asChild variant="outline" size="sm">
            <Link href={hrefFor(page.number - 1)}>
              <ChevronLeft className="mr-1 size-4" />
              Previous
            </Link>
          </Button>
        ) : (
          <Button variant="outline" size="sm" disabled>
            <ChevronLeft className="mr-1 size-4" />
            Previous
          </Button>
        )}
        {page.number < page.totalPages - 1 ? (
          <Button asChild variant="outline" size="sm">
            <Link href={hrefFor(page.number + 1)}>
              Next
              <ChevronRight className="ml-1 size-4" />
            </Link>
          </Button>
        ) : (
          <Button variant="outline" size="sm" disabled>
            Next
            <ChevronRight className="ml-1 size-4" />
          </Button>
        )}
      </div>
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
