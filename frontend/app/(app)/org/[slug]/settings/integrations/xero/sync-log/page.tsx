import Link from "next/link";
import { ChevronLeft, Activity } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
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
          href={`/org/${slug}/settings/integrations/xero`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Xero Settings
        </Link>
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Sync Log</h1>
          <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
            You do not have permission to view the sync log.
          </p>
        </div>
      </div>
    );
  }

  const pageNum = search.page ? Math.max(0, parseInt(search.page, 10) - 1) : 0;
  const pageSize = search.size ? Math.min(100, Math.max(1, parseInt(search.size, 10))) : 20;

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

  // Build base query params for pagination links
  const baseParams = new URLSearchParams();
  if (search.state) baseParams.set("state", search.state);
  if (search.entityType) baseParams.set("entityType", search.entityType);
  if (search.direction) baseParams.set("direction", search.direction);
  if (search.size) baseParams.set("size", search.size);

  function pageHref(page: number): string {
    const p = new URLSearchParams(baseParams);
    p.set("page", String(page));
    return `?${p.toString()}`;
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/integrations/xero`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Xero Settings
      </Link>

      <div>
        <div className="flex items-center gap-3">
          <Activity className="size-6 text-slate-400" />
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Sync Log</h1>
        </div>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          View and manage Xero sync entries. {totalElements > 0 && `${totalElements} total entries.`}
        </p>
      </div>

      <SyncLogFilters
        slug={slug}
        currentState={search.state}
        currentEntityType={search.entityType}
        currentDirection={search.direction}
      />

      {fetchError && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-400">
          {fetchError}
        </div>
      )}

      {!fetchError && entries.length === 0 && (
        <div className="flex flex-col items-center gap-4 py-24 text-center">
          <Activity className="size-16 text-slate-300 dark:text-slate-700" />
          <h2 className="font-display text-xl text-slate-900 dark:text-slate-100">
            No sync entries found
          </h2>
          <p className="max-w-md text-sm text-slate-600 dark:text-slate-400">
            {search.state || search.entityType || search.direction
              ? "No entries match the current filters. Try adjusting your filters."
              : "Sync entries will appear here once invoice or customer syncing begins."}
          </p>
        </div>
      )}

      {!fetchError && entries.length > 0 && (
        <>
          <div className="rounded-lg border border-slate-200 shadow-sm dark:border-slate-700">
            <SyncLogTable entries={entries} slug={slug} canReconcile={canReconcile} />
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Page {currentPage + 1} of {totalPages}
              </p>
              <div className="flex gap-1">
                {currentPage > 0 && (
                  <Link
                    href={pageHref(currentPage)}
                    className="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                  >
                    Previous
                  </Link>
                )}
                {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
                  let pageIdx: number;
                  if (totalPages <= 7) {
                    pageIdx = i;
                  } else if (currentPage < 4) {
                    pageIdx = i;
                  } else if (currentPage > totalPages - 5) {
                    pageIdx = totalPages - 7 + i;
                  } else {
                    pageIdx = currentPage - 3 + i;
                  }
                  return (
                    <Link
                      key={pageIdx}
                      href={pageHref(pageIdx + 1)}
                      className={`rounded-md border px-3 py-1.5 text-sm transition-colors ${
                        pageIdx === currentPage
                          ? "border-teal-600 bg-teal-600 text-white"
                          : "border-slate-200 text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                      }`}
                    >
                      {pageIdx + 1}
                    </Link>
                  );
                })}
                {currentPage < totalPages - 1 && (
                  <Link
                    href={pageHref(currentPage + 2)}
                    className="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                  >
                    Next
                  </Link>
                )}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
