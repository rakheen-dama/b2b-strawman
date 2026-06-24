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
import type { DealResponse, StageDto, PipelineSummaryResponse } from "@/lib/api/crm";

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

  let deals: DealResponse[] = [];
  try {
    const page = await listDeals({ size: 200 });
    deals = page.content;
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

      {stages.length === 0 ? (
        <EmptyState
          icon={KanbanSquare}
          title="No pipeline stages configured"
          description="Configure pipeline stages in settings to start tracking deals."
        />
      ) : display === "list" ? (
        <PipelineListView
          slug={slug}
          deals={deals}
          customerNames={customerNames}
          ownerNames={ownerNames}
        />
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
