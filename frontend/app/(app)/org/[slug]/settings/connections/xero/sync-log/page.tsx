import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { settingsHref } from "@/lib/settings-nav";
import { getSyncEntries } from "@/lib/api/integrations";
import { SyncLogTable } from "@/components/integrations/SyncLogTable";
import { SyncLogFilters } from "./sync-log-filters";
import type { SyncEntryResponse } from "@/lib/types";

export default async function SyncLogPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{
    state?: string;
    entityType?: string;
    direction?: string;
    page?: string;
    size?: string;
  }>;
}) {
  const { slug } = await params;
  const search = await searchParams;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;
  const canReconcile = caps.isOwner || caps.capabilities.includes("FINANCIAL_RECONCILE");

  if (!isAdmin && !caps.capabilities.includes("INTEGRATION_VIEW_SYNC_STATUS")) {
    return (
      <div className="space-y-8">
        <Link
          href={`${settingsHref(slug, "connections", "xero")}#xero`}
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
        >
          <ChevronLeft className="size-4" />
          Xero Settings
        </Link>
        <PageHeader title="Sync Log" />
        <p className="text-muted-foreground text-sm">
          You do not have permission to view the sync log.
        </p>
      </div>
    );
  }

  const parsedPage = search.page ? Number.parseInt(search.page, 10) : NaN;
  const parsedSize = search.size ? Number.parseInt(search.size, 10) : NaN;
  const pageNum = Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage - 1 : 0;
  const pageSize = Number.isFinite(parsedSize) && parsedSize > 0 ? Math.min(100, parsedSize) : 20;

  let entries: SyncEntryResponse[] = [];
  let totalPages = 0;
  let totalElements = 0;
  let currentPage = 0;
  let fetchError: string | null = null;

  try {
    const data = await getSyncEntries({
      state: search.state,
      entityType: search.entityType,
      direction: search.direction,
      page: pageNum,
      size: pageSize,
    });
    entries = data.content;
    totalPages = data.page.totalPages;
    totalElements = data.page.totalElements;
    currentPage = data.page.number;
  } catch {
    fetchError = "Failed to load sync entries. Please try again.";
  }

  // The query the pager links keep (serializable; `SyncLogTable` builds `hrefFor` from it).
  const basePath = `/org/${slug}/settings/connections/xero/sync-log`;
  const query: Record<string, string> = {};
  if (search.state) query.state = search.state;
  if (search.entityType) query.entityType = search.entityType;
  if (search.direction) query.direction = search.direction;
  if (search.size) query.size = search.size;

  const filters = (
    <SyncLogFilters
      slug={slug}
      currentState={search.state}
      currentEntityType={search.entityType}
      currentDirection={search.direction}
    />
  );

  return (
    <div className="space-y-8">
      <Link
        href={`${settingsHref(slug, "connections", "xero")}#xero`}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" />
        Xero Settings
      </Link>

      <PageHeader
        title="Sync Log"
        description={
          <>
            View and manage Xero sync entries.{" "}
            {totalElements > 0 && `${totalElements} total entries.`}
          </>
        }
      />

      {fetchError ? (
        <>
          {filters}
          <div className="border-destructive/30 bg-destructive/10 text-destructive rounded-lg border p-4 text-sm">
            {fetchError}
          </div>
        </>
      ) : (
        <SyncLogTable
          entries={entries}
          slug={slug}
          canReconcile={canReconcile}
          pageIndex={currentPage}
          pageCount={totalPages}
          basePath={basePath}
          query={query}
          filters={filters}
          emptyDescription={
            search.state || search.entityType || search.direction
              ? "No entries match the current filters. Try adjusting your filters."
              : "Sync entries will appear here once fee note or client syncing begins."
          }
        />
      )}
    </div>
  );
}
