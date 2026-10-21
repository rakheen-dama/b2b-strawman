import { Suspense } from "react";
import Link from "next/link";
import { FolderOpen } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { RequiresCapability } from "@/lib/capabilities";
import { api, handleApiError, getFieldDefinitions, getViews, getTags } from "@/lib/api";
import { listMatterRows } from "@/lib/api/matters";
import { listClients } from "@/lib/api/clients";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { getProjectTemplates } from "@/lib/api/templates";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type {
  Client,
  CreateSavedViewRequest,
  FieldDefinitionResponse,
  MatterListRow,
  OrgMember,
  SavedViewResponse,
  TagResponse,
} from "@/lib/types";
import {
  DEFAULT_MATTER_VIEW_ID,
  DORMANT_VIEW_ID,
  MATTER_SYSTEM_VIEWS,
  resolveViewSelection,
} from "@/lib/constants/system-views";
import {
  buildMatterListParams,
  parseMatterSort,
  resolveMatterColumns,
  searchMatterRows,
  sortMatterRows,
} from "@/lib/matters-list";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { NewFromTemplateWrapper } from "@/components/templates/NewFromTemplateWrapper";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import { ProjectStatusFilter } from "@/components/projects/project-status-filter";
import { MattersTable } from "@/components/matters/matters-table";
import { MatterSearch } from "@/components/matters/matter-search";
import { MatterFiltersSheet } from "@/components/matters/matter-filters-sheet";
import { EmptyState } from "@/components/empty-state";
import { createMessages } from "@/lib/messages";
import { createSavedViewAction } from "./view-actions";

/** Params the backend ignores on the saved-view path; switching to a saved view drops them. */
const SAVED_VIEW_IGNORED_PARAMS = ["tags"] as const;

function matterCount(n: number): string {
  return `${n} ${n === 1 ? "matter" : "matters"}`;
}

export default async function MattersPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;
  const param = (key: string): string | null => {
    const value = resolvedSearchParams[key];
    return typeof value === "string" ? value : null;
  };

  // Auto-open "New from Template" dialog when landing here via the
  // `/matters?new=1&customerId=...` deep-link (GAP-S3-05), e.g. from the
  // client detail Matters panel. `customerId` is not forwarded to the list.
  const autoOpenNew = param("new") === "1";
  const initialCustomerId = param("customerId") ?? undefined;

  // Saved views for the matter entity (storage-plane "PROJECT", ADR-342)
  let views: SavedViewResponse[] = [];
  try {
    views = await getViews("PROJECT");
  } catch {
    // Non-fatal: the selector shows the system views only
  }

  // System view, saved view, or the default ("Open matters"). A system id never reaches the
  // backend (it 400s on a non-UUID `view`).
  const selection = resolveViewSelection(
    MATTER_SYSTEM_VIEWS,
    views,
    param("view"),
    DEFAULT_MATTER_VIEW_ID
  );
  const savedViewActive = selection.kind === "saved";

  // R-3: relative tokens ("-90d") resolve to ISO dates here, so URLs stay bookmarkable.
  const listParams = buildMatterListParams(
    selection,
    { status: param("status"), tags: param("tags") },
    new Date()
  );

  let rows: MatterListRow[] = [];
  try {
    rows = await listMatterRows(listParams);
  } catch (error) {
    handleApiError(error);
  }

  // Field definitions name the saved view's `cf:<slug>` columns
  let fieldDefs: FieldDefinitionResponse[] = [];
  try {
    fieldDefs = await getFieldDefinitions("PROJECT");
  } catch {
    // Non-fatal: custom-field columns are dropped
  }

  let allTags: TagResponse[] = [];
  try {
    allTags = await getTags();
  } catch {
    // Non-fatal
  }

  // F-8: the Trust column shows when trust accounting is on; cells are "—" when not visible.
  const trustVisible = await isModuleEnabledServer("trust_accounting");

  // Active project templates for "New from Template" (admin/owner only)
  let activeTemplates: ProjectTemplateResponse[] = [];
  let orgMembers: OrgMember[] = [];
  let allCustomers: Client[] = [];

  if (isAdmin) {
    try {
      const allTemplates = await getProjectTemplates();
      activeTemplates = allTemplates.filter((t) => t.active);
    } catch {
      // Non-fatal: hide "New from Template" button
    }

    // Only fetch members + clients if there are active templates to instantiate
    if (activeTemplates.length > 0) {
      const [membersResult, customersResult] = await Promise.allSettled([
        api.get<OrgMember[]>("/api/members"),
        listClients(),
      ]);
      if (membersResult.status === "fulfilled") orgMembers = membersResult.value;
      if (customersResult.status === "fulfilled") allCustomers = customersResult.value;
    }
  }

  // R-1 / F-2: the backend has no sort or search param; both run here over the full list.
  const search = param("search");
  const sort = parseMatterSort(
    param("sort"),
    param("dir"),
    selection.kind === "system" && selection.view.id === DORMANT_VIEW_ID
  );
  const visibleRows = sortMatterRows(searchMatterRows(rows, search), sort.key, sort.dir);

  const columns = resolveMatterColumns(savedViewActive ? selection.view.columns : null, {
    trustVisible,
    customFieldSlugs: fieldDefs.map((f) => f.slug),
  });
  const customFieldNames = Object.fromEntries(fieldDefs.map((f) => [f.slug, f.name]));

  const query: Record<string, string> = {};
  for (const [key, value] of Object.entries(resolvedSearchParams)) {
    if (typeof value === "string") query[key] = value;
  }

  // F-3: URL `tags` filters system views only; on a saved view it is ignored, so it does not count.
  const filtered =
    !!search?.trim() ||
    (!savedViewActive && !!param("tags")) ||
    !!param("status") ||
    selection.view.id !== DEFAULT_MATTER_VIEW_ID;

  async function handleCreateView(req: CreateSavedViewRequest) {
    "use server";
    return createSavedViewAction(slug, req);
  }

  const { t } = createMessages("empty-states");

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Matters</h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {matterCount(visibleRows.length)}
          </p>
        </div>
        <RequiresCapability cap="PROJECT_MANAGEMENT">
          <div className="flex items-center gap-2">
            <NewFromTemplateWrapper
              slug={slug}
              templates={activeTemplates}
              orgMembers={orgMembers}
              customers={allCustomers}
              autoOpen={autoOpenNew}
              initialCustomerId={initialCustomerId}
            />
            <CreateProjectDialog slug={slug} />
          </div>
        </RequiresCapability>
      </div>

      {/* Toolbar: views, search, status, filters */}
      <div className="space-y-3">
        <Suspense fallback={null}>
          <ViewSelectorClient
            entityType="PROJECT"
            views={views}
            canCreate={true}
            canCreateShared={isAdmin}
            slug={slug}
            allTags={allTags}
            fieldDefinitions={fieldDefs}
            onSave={handleCreateView}
            systemViews={MATTER_SYSTEM_VIEWS}
            defaultViewId={DEFAULT_MATTER_VIEW_ID}
            clearOnSavedView={SAVED_VIEW_IGNORED_PARAMS}
          />
        </Suspense>
        <div className="flex flex-wrap items-center gap-3">
          <Suspense fallback={null}>
            <MatterSearch />
          </Suspense>
          <Suspense fallback={null}>
            <ProjectStatusFilter slug={slug} />
          </Suspense>
          {/* F-3: the backend ignores tags on the saved-view path, so the filter hides there. */}
          {!savedViewActive && allTags.length > 0 && (
            <Suspense fallback={null}>
              <MatterFiltersSheet allTags={allTags} />
            </Suspense>
          )}
        </div>
      </div>

      {/* Matters table or empty state */}
      {visibleRows.length === 0 ? (
        filtered ? (
          <div className="rounded-lg border border-dashed border-slate-200 px-6 py-10 text-center dark:border-slate-800">
            <p className="text-sm text-slate-600 dark:text-slate-400">
              No matters match this view.
            </p>
            <Link
              href={`/org/${slug}/matters`}
              className="mt-2 inline-block text-sm font-medium text-teal-600 hover:underline"
            >
              Clear filters
            </Link>
          </div>
        ) : (
          <EmptyState
            icon={FolderOpen}
            title={t("projects.list.heading")}
            description={
              isAdmin ? t("projects.list.description") : t("projects.list.descriptionMember")
            }
            action={<CreateProjectDialog slug={slug} />}
          />
        )
      ) : (
        <div className="space-y-2">
          <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
            <MattersTable
              slug={slug}
              rows={visibleRows}
              columns={columns}
              customFieldNames={customFieldNames}
              sort={sort}
              query={query}
              caption={`Matters — ${selection.view.name}`}
            />
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400" data-testid="matters-footer">
            Showing {matterCount(visibleRows.length)}
          </p>
        </div>
      )}
    </div>
  );
}
