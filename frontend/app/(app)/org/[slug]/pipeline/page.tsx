import { Suspense } from "react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, getViews, getTags, getFieldDefinitions } from "@/lib/api";
import { getStages, listDeals, pipelineSummary } from "@/lib/api/crm";
import { RequiresCapability } from "@/lib/capabilities";
import { PermissionDenied } from "@/components/permission-denied";
import { EmptyState } from "@/components/empty-state";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import { PipelineFilters } from "@/components/pipeline/PipelineFilters";
import { PipelineBoard } from "@/components/pipeline/PipelineBoard";
import { PipelineListView } from "@/components/pipeline/PipelineListView";
import { IntakeDialog } from "@/components/pipeline/IntakeDialog";
import { createSavedViewAction } from "./view-actions";
import { formatCurrency } from "@/lib/format";
import { KanbanSquare } from "lucide-react";
import type {
  SavedViewResponse,
  TagResponse,
  FieldDefinitionResponse,
  Customer,
  OrgMember,
} from "@/lib/types";
import type {
  DealResponse,
  StageDto,
  PipelineSummaryResponse,
  ListDealsParams,
  DealStatus,
} from "@/lib/api/crm";

// Max deals loaded into the board/list in a single request. The backend list is
// paginated; full board pagination is out of scope for this PR (deferred to a
// follow-up). This cap is explicit so larger orgs see a clear truncation note
// rather than silently dropping deals.
// TODO: paginate the board/list instead of capping at a fixed page size.
const MAX_DEALS = 500;

const VALID_DEAL_STATUSES: ReadonlySet<DealStatus> = new Set(["OPEN", "WON", "LOST"]);

function strParam(
  params: Record<string, string | string[] | undefined>,
  key: string
): string | undefined {
  const v = params[key];
  return typeof v === "string" && v.length > 0 ? v : undefined;
}

export default async function PipelinePage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("VIEW_DEALS")) {
    return <PermissionDenied featureName="Pipeline" dashboardHref={`/org/${slug}/dashboard`} />;
  }

  const isAdmin = capData.isAdmin || capData.isOwner;
  const display = resolvedSearchParams.display === "list" ? "list" : "board";

  // Core data — board/summary are important; degrade gracefully on failure.
  let stages: StageDto[] = [];
  try {
    stages = await getStages();
  } catch {
    /* no stages configured yet */
  }

  // Wire the URL filter params the backend GET /api/deals supports (stageId,
  // ownerId, customerId, status, source, fromDate, toDate) into the list
  // request, so the board/list reflects the active filters instead of ignoring
  // them. Saved-view (`view`) and `tags` filtering ARE now forwarded too: the
  // deal-list endpoint accepts `view` (saved-view UUID) and `tags` (CSV of tag
  // slugs, ANDed) as of slice 574B, so those UI controls narrow the dataset
  // server-side.
  const rawStatus = strParam(resolvedSearchParams, "status");
  const rawTags = strParam(resolvedSearchParams, "tags");
  const tags = rawTags
    ? rawTags
        .split(",")
        .map((t) => t.trim())
        .filter((t) => t.length > 0)
    : undefined;
  const dealFilters: ListDealsParams = {
    stageId: strParam(resolvedSearchParams, "stageId"),
    ownerId: strParam(resolvedSearchParams, "ownerId"),
    customerId: strParam(resolvedSearchParams, "customerId"),
    status:
      rawStatus && VALID_DEAL_STATUSES.has(rawStatus as DealStatus)
        ? (rawStatus as DealStatus)
        : undefined,
    source: strParam(resolvedSearchParams, "source"),
    fromDate: strParam(resolvedSearchParams, "fromDate"),
    toDate: strParam(resolvedSearchParams, "toDate"),
    tags: tags && tags.length > 0 ? tags : undefined,
    view: strParam(resolvedSearchParams, "view"),
    sort: strParam(resolvedSearchParams, "sort"),
  };

  let deals: DealResponse[] = [];
  let dealsTruncated = false;
  try {
    const page = await listDeals({ ...dealFilters, size: MAX_DEALS });
    deals = page.content;
    dealsTruncated = page.page.totalElements > deals.length;
  } catch {
    /* non-fatal: show empty board */
  }

  let summary: PipelineSummaryResponse | null = null;
  try {
    summary = await pipelineSummary();
  } catch {
    /* non-fatal */
  }

  let views: SavedViewResponse[] = [];
  try {
    views = await getViews("DEAL");
  } catch {
    /* non-fatal */
  }

  let allTags: TagResponse[] = [];
  try {
    allTags = await getTags();
  } catch {
    /* non-fatal */
  }

  let dealFieldDefs: FieldDefinitionResponse[] = [];
  try {
    dealFieldDefs = await getFieldDefinitions("DEAL");
  } catch {
    /* non-fatal */
  }

  // Resolve customer + owner display names for cards/rows.
  let customers: Customer[] = [];
  try {
    customers = await api.get<Customer[]>("/api/customers");
  } catch {
    /* non-fatal */
  }
  const customerNames: Record<string, string> = {};
  for (const c of customers) customerNames[c.id] = c.name;
  const customerOptions = customers.map((c) => ({ id: c.id, name: c.name }));

  let members: OrgMember[] = [];
  try {
    members = await api.get<OrgMember[]>("/api/members");
  } catch {
    /* non-fatal */
  }
  const ownerNames: Record<string, string> = {};
  for (const m of members) ownerNames[m.id] = m.name;

  const currency = summary?.currency ?? deals[0]?.valueCurrency ?? "ZAR";
  const winRatePct = summary ? Math.round(summary.winRate * 100) : 0;

  async function handleCreateView(req: import("@/lib/types").CreateSavedViewRequest) {
    "use server";
    return createSavedViewAction(slug, req);
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Pipeline</h1>
          {summary && (
            <div className="mt-2 flex flex-wrap items-center gap-6">
              <div>
                <p className="text-xs tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  Open weighted value
                </p>
                <p className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                  {formatCurrency(summary.openWeightedValue, currency)}
                </p>
              </div>
              <div>
                <p className="text-xs tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  Win rate
                </p>
                <p className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                  {winRatePct}%
                </p>
              </div>
            </div>
          )}
        </div>
        <RequiresCapability cap="MANAGE_DEALS">
          <IntakeDialog slug={slug} customers={customerOptions} stages={stages} />
        </RequiresCapability>
      </div>

      <Suspense fallback={null}>
        <ViewSelectorClient
          entityType="DEAL"
          views={views}
          canCreate
          canCreateShared={isAdmin}
          slug={slug}
          allTags={allTags}
          fieldDefinitions={dealFieldDefs}
          onSave={handleCreateView}
        />
      </Suspense>

      <Suspense fallback={null}>
        <PipelineFilters allTags={allTags} display={display} />
      </Suspense>

      {dealsTruncated && (
        <p className="text-xs text-slate-500 dark:text-slate-400">
          Showing the first {MAX_DEALS} deals. Use the filters above to narrow the list.
        </p>
      )}

      {stages.length === 0 ? (
        <EmptyState
          icon={KanbanSquare}
          title="No pipeline stages configured"
          description="Configure pipeline stages in settings to start tracking deals."
        />
      ) : display === "list" ? (
        <PipelineListView deals={deals} customerNames={customerNames} ownerNames={ownerNames} />
      ) : (
        <PipelineBoard
          slug={slug}
          stages={stages}
          deals={deals}
          customerNames={customerNames}
          ownerNames={ownerNames}
          canManage={isAdmin || capData.capabilities.includes("MANAGE_DEALS")}
        />
      )}
    </div>
  );
}
