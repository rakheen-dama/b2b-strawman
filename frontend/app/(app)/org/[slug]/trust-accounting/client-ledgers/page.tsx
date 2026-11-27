import { notFound } from "next/navigation";
import Link from "next/link";
import { AlertCircle, Users } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import {
  fetchClientLedgers,
  type ClientLedgerPage,
} from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
import { EmptyState } from "@/components/empty-state";
import { ClientLedgersDataTable } from "@/components/trust/client-ledgers-data-table";
import { cn } from "@/lib/utils";
import { searchParamsToQuery } from "@/lib/search-params-query";

const PILL_CLASS = "rounded-full px-3 py-1 text-sm transition-colors";
const PILL_ACTIVE = "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900";
const PILL_IDLE =
  "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700";

// ── Page ──────────────────────────────────────────────────────────

export default async function ClientLedgersPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{
    nonZeroOnly?: string;
    search?: string;
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

  // Fetch client ledgers
  let ledgerPage: ClientLedgerPage | null = null;
  let fetchError = false;

  if (accountId) {
    try {
      ledgerPage = await fetchClientLedgers(accountId, {
        page: search.page ? parseInt(search.page, 10) : 0,
        size: 20,
        nonZeroOnly: search.nonZeroOnly === "true",
        search: search.search,
      });
    } catch {
      fetchError = true;
    }
  }

  const currency = settings.defaultCurrency ?? "ZAR";
  const basePath = `/org/${slug}/trust-accounting/client-ledgers`;
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

  // The non-zero toggle and the search form (its own "Search" button; the GET form drops `page`):
  // the table's toolbar, or on their own above the error when the read failed.
  const filters = (
    <div className="flex flex-wrap items-center gap-4" data-testid="ledger-filters">
      <Link
        href={filterUrl({
          nonZeroOnly: search.nonZeroOnly === "true" ? undefined : "true",
        })}
        className={cn(PILL_CLASS, search.nonZeroOnly === "true" ? PILL_ACTIVE : PILL_IDLE)}
        data-testid="non-zero-filter"
      >
        Non-zero balances only
      </Link>

      <form action={basePath} method="GET" className="flex items-center gap-2">
        {search.nonZeroOnly === "true" && <input type="hidden" name="nonZeroOnly" value="true" />}
        <Input
          type="text"
          name="search"
          placeholder="Search clients..."
          defaultValue={search.search ?? ""}
          className="placeholder:text-muted-foreground h-8 w-56"
          data-testid="client-search"
        />
        <Button type="submit" variant="outline" size="sm">
          Search
        </Button>
      </form>
    </div>
  );

  return (
    <div className="space-y-8" data-testid="client-ledgers-page">
      <PageHeader title="Client Ledgers" description="Trust account balances by client" />

      {/* No Account State */}
      {!accountId && !accountFetchError && (
        <Card>
          <CardContent>
            <EmptyState
              variant="compact"
              icon={Users}
              title="No trust account configured. Set up a trust account to view client ledgers."
              description={null}
            />
          </CardContent>
        </Card>
      )}

      {/* Error State: the filters stay usable */}
      {(fetchError || accountFetchError) && (
        <>
          {filters}
          <Card>
            <CardContent>
              <div data-testid="error-state">
                <EmptyState
                  variant="compact"
                  icon={AlertCircle}
                  title="Unable to load client ledgers. Please try again later."
                  description={null}
                />
              </div>
            </CardContent>
          </Card>
        </>
      )}

      {/* Client Ledger Table */}
      {ledgerPage && (
        <Card>
          <CardHeader>
            <CardTitle>Client Balances</CardTitle>
            <CardDescription>
              {ledgerPage.content.length} client
              {ledgerPage.content.length !== 1 ? "s" : ""} found
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ClientLedgersDataTable
              rows={ledgerPage.content}
              currency={currency}
              pageIndex={ledgerPage.pageNumber}
              pageCount={ledgerPage.totalPages}
              basePath={basePath}
              query={query}
              toolbar={{ filters }}
              empty={
                <div data-testid="empty-state">
                  <EmptyState
                    variant="inline"
                    icon={Users}
                    title="No client ledgers match the current filters"
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
