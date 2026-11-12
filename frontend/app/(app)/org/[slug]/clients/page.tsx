import { Suspense } from "react";
import Link from "next/link";
import { UserRound, Users } from "lucide-react";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { handleApiError, getFieldDefinitions, getViews, getTags } from "@/lib/api";
import { listClientRows } from "@/lib/api/clients";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { RequiresCapability } from "@/lib/capabilities";
import type {
  ClientListRow,
  CreateSavedViewRequest,
  FieldDefinitionResponse,
  LifecycleStatus,
  SavedViewResponse,
  TagResponse,
} from "@/lib/types";
import {
  ALL_CLIENTS_VIEW_ID,
  CLIENT_VIEW_OPTIONS,
  resolveViewSelection,
} from "@/lib/constants/system-views";
import {
  applySavedViewLifecycle,
  buildClientListParams,
  isLifecycleStatus,
  parseClientSort,
  resolveClientColumns,
  savedViewShowIncomplete,
  searchClientRows,
  sortClientRows,
} from "@/lib/clients-list";
import { CreateCustomerDialog } from "@/components/customers/create-customer-dialog";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import { ClientsDataTable } from "@/components/clients/clients-table";
import { ClientSearch } from "@/components/clients/client-search";
import { ClientFiltersSheet } from "@/components/clients/client-filters-sheet";
import { ClientLifecycleFilter } from "@/components/clients/client-lifecycle-filter";
import { EmptyState } from "@/components/empty-state";
import { PermissionDenied } from "@/components/permission-denied";
import { createMessages } from "@/lib/messages";
import { createSavedViewAction } from "./view-actions";

/** Params the backend ignores on the saved-view path; switching to a saved view drops them. */
const SAVED_VIEW_IGNORED_PARAMS = ["tags", "lifecycleStatus"] as const;

function clientCount(n: number): string {
  return `${n} ${n === 1 ? "client" : "clients"}`;
}

export default async function ClientsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner && !caps.capabilities.includes("CUSTOMER_MANAGEMENT")) {
    return <PermissionDenied featureName="Clients" dashboardHref={`/org/${slug}/dashboard`} />;
  }

  const isAdmin = caps.isAdmin || caps.isOwner;
  const param = (key: string): string | null => {
    const value = resolvedSearchParams[key];
    return typeof value === "string" ? value : null;
  };

  // Saved views for the client entity (storage-plane "CUSTOMER", ADR-342)
  let views: SavedViewResponse[] = [];
  try {
    views = await getViews("CUSTOMER");
  } catch {
    // Non-fatal: the selector shows the system views only
  }

  // D-1(B): no `view` param → "All clients". A system id never reaches the backend (it 400s on a
  // non-UUID `view`).
  const selection = resolveViewSelection(
    CLIENT_VIEW_OPTIONS,
    views,
    param("view"),
    ALL_CLIENTS_VIEW_ID
  );
  const savedViewActive = selection.kind === "saved";

  const listParams = buildClientListParams(selection, {
    lifecycleStatus: param("lifecycleStatus"),
    showIncomplete: param("showIncomplete"),
    tags: param("tags"),
  });

  let rows: ClientListRow[] = [];
  try {
    rows = await listClientRows(listParams);
  } catch (error) {
    handleApiError(error);
  }

  // Field definitions name the saved view's `cf:<slug>` columns
  let fieldDefs: FieldDefinitionResponse[] = [];
  try {
    fieldDefs = await getFieldDefinitions("CUSTOMER");
  } catch {
    // Non-fatal: custom-field columns are dropped
  }

  let allTags: TagResponse[] = [];
  try {
    allTags = await getTags();
  } catch {
    // Non-fatal
  }

  // F-8: the Trust balance column shows when trust accounting is on; cells are "—" when redacted.
  const trustVisible = await isModuleEnabledServer("trust_accounting");

  // R-1 / D-7: the backend has no sort or search param; both run here over the full list.
  const search = param("search");
  const sort = parseClientSort(param("sortBy"), param("sortDir"));
  const viewRows = applySavedViewLifecycle(rows, selection);
  const visibleRows = sortClientRows(searchClientRows(viewRows, search), sort.key, sort.dir);

  const columns = resolveClientColumns(savedViewActive ? selection.view.columns : null, {
    trustVisible,
    customFieldSlugs: fieldDefs.map((f) => f.slug),
  });
  const customFieldNames = Object.fromEntries(fieldDefs.map((f) => [f.slug, f.name]));
  // Columns the user picked for a saved view always render; only the default set drops columns.
  const explicitColumns = savedViewActive && (selection.view.columns?.length ?? 0) > 0;

  const query: Record<string, string> = {};
  for (const [key, value] of Object.entries(resolvedSearchParams)) {
    if (typeof value === "string") query[key] = value;
  }

  const viewLifecycle =
    selection.kind === "system" && isLifecycleStatus(selection.view.params.lifecycleStatus)
      ? (selection.view.params.lifecycleStatus as LifecycleStatus)
      : null;
  const activeLifecycle = isLifecycleStatus(listParams.lifecycleStatus)
    ? listParams.lifecycleStatus
    : null;
  // The effective filter (URL or the saved view's stored filter); a stored one is not toggleable.
  const showIncomplete = listParams.showIncomplete === "true";
  const showIncompleteLocked = savedViewShowIncomplete(selection);

  // Anything narrowing the list other than "Show incomplete" (F-3: URL tags count on system views
  // only, D-2: so does the URL lifecycle).
  const filtered =
    !!search?.trim() ||
    (!savedViewActive && (!!param("tags") || activeLifecycle !== null)) ||
    selection.view.id !== ALL_CLIENTS_VIEW_ID;

  async function handleCreateView(req: CreateSavedViewRequest) {
    "use server";
    return createSavedViewAction(slug, req);
  }

  const { t } = createMessages("empty-states");

  return (
    <div className="space-y-6">
      <PageHeader
        title="Clients"
        description={clientCount(visibleRows.length)}
        actions={
          <RequiresCapability cap="CUSTOMER_MANAGEMENT">
            <CreateCustomerDialog slug={slug} />
          </RequiresCapability>
        }
      />

      <ClientsDataTable
        slug={slug}
        rows={visibleRows}
        columns={columns}
        customFieldNames={customFieldNames}
        sort={sort}
        query={query}
        caption={`Clients — ${selection.view.name}`}
        hideLowPriorityColumns={!explicitColumns}
        toolbar={{
          views: (
            <Suspense fallback={null}>
              <ViewSelectorClient
                entityType="CUSTOMER"
                views={views}
                canCreate={true}
                canCreateShared={isAdmin}
                slug={slug}
                allTags={allTags}
                fieldDefinitions={fieldDefs}
                onSave={handleCreateView}
                systemViews={CLIENT_VIEW_OPTIONS}
                defaultViewId={ALL_CLIENTS_VIEW_ID}
                clearOnSavedView={SAVED_VIEW_IGNORED_PARAMS}
              />
            </Suspense>
          ),
          search: (
            <Suspense fallback={null}>
              <ClientSearch />
            </Suspense>
          ),
          filters: (
            <>
              <ClientLifecycleFilter
                slug={slug}
                query={query}
                activeLifecycle={activeLifecycle}
                viewLifecycle={viewLifecycle}
                showLifecycle={!savedViewActive}
                showIncomplete={showIncomplete}
                showIncompleteLocked={showIncompleteLocked}
              />
              {/* F-3: the backend ignores tags on the saved-view path, so the filter hides there. */}
              {!savedViewActive && allTags.length > 0 && (
                <Suspense fallback={null}>
                  <ClientFiltersSheet allTags={allTags} />
                </Suspense>
              )}
            </>
          ),
        }}
        empty={
          filtered ? (
            <div className="border-border rounded-lg border border-dashed px-6 py-10 text-center">
              <p className="text-muted-foreground text-sm">No clients match this view.</p>
              <Link
                href={`/org/${slug}/clients`}
                className="text-primary mt-2 inline-block text-sm font-medium underline-offset-4 hover:underline"
              >
                Clear filters
              </Link>
            </div>
          ) : showIncomplete ? (
            <EmptyState
              icon={UserRound}
              title="All clients are complete"
              description="All clients have 100% completeness."
            />
          ) : (
            <EmptyState
              icon={Users}
              title="No clients yet"
              description={
                isAdmin ? t("customers.list.description") : t("customers.list.descriptionMember")
              }
              action={isAdmin ? <CreateCustomerDialog slug={slug} /> : undefined}
            />
          )
        }
        footer={
          <p className="text-muted-foreground text-xs" data-testid="clients-footer">
            Showing {clientCount(visibleRows.length)}
          </p>
        }
      />
    </div>
  );
}
