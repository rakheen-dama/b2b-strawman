import { getAuthContext, getCurrentUserEmail } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { isModuleEnabledServer } from "@/lib/api/settings";
import {
  api,
  handleApiError,
  getFieldDefinitions,
  getFieldGroups,
  getGroupMembers,
  getTags,
  getTemplates,
  getViews,
} from "@/lib/api";
import {
  getMatter,
  getMatterBudget,
  getMatterProfitability,
  getMatterSetupStatus,
  getMatterTags,
  getMatterTimeSummary,
  getMatterTimeSummaryByMember,
  getMatterTimeSummaryByTask,
  listMatterClients,
  listMatterDocuments,
  listMatterMembers,
  listMatterTasks,
} from "@/lib/api/matters";
import { getClientFicaStatus } from "@/lib/api/clients";
import type {
  OrgMember,
  Matter,
  Client,
  Document,
  MatterMember,
  Task,
  MatterTimeSummary,
  MemberTimeSummary,
  TaskTimeSummary,
  BillingRate,
  OrgSettings,
  BudgetStatusResponse,
  MatterProfitabilityResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
  TagResponse,
  TemplateListResponse,
  SavedViewResponse,
} from "@/lib/types";
import { MatterHeaderCard } from "@/components/projects/matter-header-card";
import { MatterDetailsTab } from "@/components/projects/matter-details-tab";
import { MatterFieldsTab } from "@/components/projects/matter-fields-tab";
import { DocumentsPanel } from "@/components/documents/documents-panel";
import { CorrespondenceList } from "@/components/correspondence/correspondence-list";
import { getProjectCorrespondence } from "@/lib/api/correspondence";
import type { CorrespondenceListItem } from "@/lib/api/correspondence";
import { TaskListPanel } from "@/components/tasks/task-list-panel";
import { TimeSummaryPanel } from "@/components/projects/time-summary-panel";
import { ActivityFeed } from "@/components/activity/activity-feed";
import { ProjectCustomersPanel } from "@/components/projects/project-customers-panel";
import { MatterTabs } from "@/components/matters/matter-tabs";
import { ProjectMembersPanel } from "@/components/projects/project-members-panel";
import { ProjectAuditTab } from "@/components/projects/project-audit-tab";
import { ClosureHistorySection } from "@/components/projects/closure-history-section";
import { ProjectRatesTab } from "@/components/rates/project-rates-tab";
import { BudgetPanel } from "@/components/budget/budget-panel";
import { ProjectFinancialsTab } from "@/components/profitability/project-financials-tab";
import { OverviewTab } from "@/components/projects/overview-tab";
import { GeneratedDocumentsList } from "@/components/templates/GeneratedDocumentsList";
import { ProjectCommentsSection } from "@/components/projects/project-comments-section";
import {
  getProjectRequests,
  type InformationRequestResponse,
} from "@/lib/api/information-requests";
import { RequestList } from "@/components/information-requests/request-list";
import { CreateRequestDialog } from "@/components/information-requests/create-request-dialog";
import { getAiProfile } from "@/lib/api/ai";
import type { MatterSetupStatus, FicaStatus } from "@/lib/types";
import type { SetupStep } from "@/components/setup/types";
import { ArchivedProjectBanner } from "@/components/projects/archived-project-banner";
import { OverflowActionsMenu } from "@/components/projects/overflow-actions-menu";
import { ProjectCourtDatesTab } from "@/components/legal/project-court-dates-tab";
import { ProjectAdversePartiesTab } from "@/components/legal/project-adverse-parties-tab";
import { ProjectDisbursementsTab } from "@/components/legal/project-disbursements-tab";
import { ProjectStatementsTab } from "@/components/legal/project-statements-tab";
import { TrustBalanceCard } from "@/components/trust/TrustBalanceCard";
import { RecordRecentItem } from "@/components/record-recent-item";
import { createSavedViewAction } from "./view-actions";
import { PROMOTED_PROJECT_SLUGS, PROMOTED_TASK_SLUGS } from "@/lib/constants/promoted-field-slugs";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";

export default async function ProjectDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  // Auth guard (keycloak-bff re-login funnel).
  await getAuthContext();
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;
  const isOwner = caps.isOwner;
  // Court-date / prescription writes need MANAGE_LEGAL (backend @RequiresCapability).
  const canManageLegal = isAdmin || caps.capabilities.includes("MANAGE_LEGAL");

  let project: Matter;
  try {
    project = await getMatter(id);
  } catch (error) {
    handleApiError(error);
  }

  const canEdit = isAdmin || project.matterRole === "lead";
  const isCurrentLead = project.matterRole === "lead";
  const canManage = isAdmin || isCurrentLead;
  // TODO(epic-535): re-add roleBadge to MatterSidebar (projectRole omitted in 532B)

  // Setup guidance data (Epic 112A)
  let setupStatus: MatterSetupStatus | null = null;
  try {
    setupStatus = await getMatterSetupStatus(id);
  } catch {
    // Non-fatal: setup guidance cards will not render if fetch fails
  }

  // Map setup status to steps
  const setupSteps: SetupStep[] = setupStatus
    ? [
        {
          label: "Customer assigned",
          complete: setupStatus.clientAssigned,
          actionHref: `?tab=clients`,
        },
        {
          label: "Rate card configured",
          complete: setupStatus.rateCardConfigured,
          actionHref: `?tab=rates`,
          permissionRequired: true,
        },
        {
          label: "Budget set",
          complete: setupStatus.budgetConfigured,
          actionHref: `?tab=budget`,
        },
        {
          label: "Team members added",
          complete: setupStatus.teamAssigned,
          actionHref: `?tab=team`,
        },
        {
          label:
            setupStatus.requiredFields.total === 0
              ? "No required fields defined"
              : `Required fields filled (${setupStatus.requiredFields.filled}/${setupStatus.requiredFields.total})`,
          complete:
            setupStatus.requiredFields.total === 0 ||
            setupStatus.requiredFields.filled === setupStatus.requiredFields.total,
          actionHref: "?tab=fields",
        },
      ]
    : [];

  // AI configuration status — needed for contract review button in documents tab
  const canExecuteAi = caps.capabilities.includes("AI_EXECUTE");
  const canReviewGates = caps.capabilities.includes("AI_REVIEW");
  let isAiConfigured = false;
  if (caps.capabilities.includes("AI_MANAGE")) {
    try {
      const aiProfile = await getAiProfile();
      isAiConfigured = aiProfile.coldStartCompleted;
    } catch {
      // Non-fatal: panel will show disabled state
    }
  } else if (canExecuteAi) {
    // MEMBER with AI_EXECUTE: they wouldn't have this capability without setup being done
    isAiConfigured = true;
  }

  let documents: Document[] = [];
  try {
    documents = await listMatterDocuments(id);
  } catch {
    // Non-fatal: show empty documents list if fetch fails
  }

  let correspondence: CorrespondenceListItem[] = [];
  try {
    correspondence = (await getProjectCorrespondence(id, { size: 50 })).content;
  } catch {
    // Non-fatal: show empty correspondence list if fetch fails
  }

  let members: MatterMember[] = [];
  try {
    members = await listMatterMembers(id);
  } catch {
    // Non-fatal: show empty members list if fetch fails
  }

  let tasks: Task[] = [];
  try {
    tasks = await listMatterTasks(id);
  } catch {
    // Non-fatal: show empty tasks list if fetch fails
  }

  let customers: Client[] = [];
  try {
    customers = await listMatterClients(id);
  } catch {
    // Non-fatal: show empty customers list if fetch fails
  }

  // Fetch information requests for the Requests tab (only if project has customers)
  let projectRequests: InformationRequestResponse[] = [];
  if (customers.length > 0) {
    try {
      projectRequests = await getProjectRequests(id);
    } catch {
      // Non-fatal: requests tab will show empty state
    }
  }

  // FICA onboarding status for the matter's primary customer (GAP-L-46).
  // Surfaces on the Overview tab as a small status tile. Fetch is
  // non-fatal — when the endpoint is unreachable or the profile
  // doesn't carry a FICA template pack, the card renders a soft
  // "Status unavailable" state rather than breaking the page.
  let ficaStatus: FicaStatus | null = null;
  if (customers.length > 0) {
    try {
      ficaStatus = await getClientFicaStatus(customers[0].id);
    } catch {
      ficaStatus = null;
    }
  }

  // Time summary data for the "Time" tab
  let timeSummary: MatterTimeSummary = {
    billableMinutes: 0,
    nonBillableMinutes: 0,
    totalMinutes: 0,
    contributorCount: 0,
    entryCount: 0,
  };
  let timeSummaryByTask: TaskTimeSummary[] = [];
  let timeSummaryByMember: MemberTimeSummary[] | null = null;
  try {
    const [summaryRes, byTaskRes, byMemberRes] = await Promise.all([
      getMatterTimeSummary(id),
      getMatterTimeSummaryByTask(id),
      getMatterTimeSummaryByMember(id).catch(() => null),
    ]);
    timeSummary = summaryRes;
    timeSummaryByTask = byTaskRes;
    timeSummaryByMember = byMemberRes;
  } catch {
    // Non-fatal: show empty time summary if fetch fails
  }

  // Disbursements module flag for the Overview KPI card (ADR-340: one Disbursements tab, module-derived)
  const disbursementsEnabled = await isModuleEnabledServer("disbursements");

  // Billing rates + settings for the "Rates" tab (only fetched for users who can manage)
  let projectBillingRates: BillingRate[] = [];
  let defaultCurrency = "USD";
  if (canManage) {
    try {
      const [ratesRes, settingsRes] = await Promise.all([
        api.get<{ content: BillingRate[] }>(`/api/billing-rates?matterId=${id}`),
        api.get<OrgSettings>("/api/settings").catch(() => null),
      ]);
      projectBillingRates = ratesRes?.content ?? [];
      if (settingsRes?.defaultCurrency) {
        defaultCurrency = settingsRes.defaultCurrency;
      }
    } catch {
      // Non-fatal: show empty rates tab if fetch fails
    }
  }

  // Budget status for the "Budget" tab
  let budgetStatus: BudgetStatusResponse | null = null;
  try {
    budgetStatus = await getMatterBudget(id);
  } catch {
    // Non-fatal: 404 means no budget set, other errors show empty state
  }

  // Project profitability for the "Financials" tab (lead/admin/owner)
  let projectProfitability: MatterProfitabilityResponse | null = null;
  if (canManage) {
    try {
      projectProfitability = await getMatterProfitability(id);
    } catch {
      // Non-fatal: show empty state in financials tab
    }
  }

  // Resolve current user's backend member ID for "My Tasks" filter and claim/release actions.
  // Match current user email against org members list from the backend.
  let currentMemberId: string | null = null;
  try {
    const [email, orgMembers] = await Promise.all([
      getCurrentUserEmail(),
      api.get<OrgMember[]>("/api/members"),
    ]);
    if (email) {
      const match = orgMembers.find((m) => m.email === email);
      if (match) currentMemberId = match.id;
    }
  } catch {
    // Non-fatal: "My Tasks" filter won't work without member ID
  }

  // Custom field definitions and groups for the Custom Fields section
  let projectFieldDefs: FieldDefinitionResponse[] = [];
  let projectFieldGroups: FieldGroupResponse[] = [];
  const projectGroupMembers: Record<string, FieldGroupMemberResponse[]> = {};
  try {
    const [defs, groups] = await Promise.all([
      getFieldDefinitions("PROJECT"),
      getFieldGroups("PROJECT"),
    ]);
    projectFieldDefs = defs.filter((d) => !PROMOTED_PROJECT_SLUGS.has(d.slug));
    projectFieldGroups = groups;

    // Fetch members for each applied group
    const appliedGroups = project.appliedFieldGroups ?? [];
    if (appliedGroups.length > 0) {
      const memberResults = await Promise.allSettled(
        appliedGroups.map((gId) => getGroupMembers(gId))
      );
      memberResults.forEach((result, i) => {
        if (result.status === "fulfilled") {
          projectGroupMembers[appliedGroups[i]] = result.value;
        }
      });
    }
  } catch {
    // Non-fatal: custom fields section won't render
  }

  // Custom field definitions for tasks (for TaskDetailSheet)
  let taskFieldDefs: FieldDefinitionResponse[] = [];
  let taskFieldGroups: FieldGroupResponse[] = [];
  const taskGroupMembers: Record<string, FieldGroupMemberResponse[]> = {};
  try {
    const [defs, groups] = await Promise.all([getFieldDefinitions("TASK"), getFieldGroups("TASK")]);
    taskFieldDefs = defs.filter((d) => !PROMOTED_TASK_SLUGS.has(d.slug));
    taskFieldGroups = groups;

    // Pre-fetch members for all active TASK field groups
    const activeGroupIds = groups.filter((g) => g.active).map((g) => g.id);
    if (activeGroupIds.length > 0) {
      const memberResults = await Promise.allSettled(
        activeGroupIds.map((gId) => getGroupMembers(gId))
      );
      memberResults.forEach((result, i) => {
        if (result.status === "fulfilled") {
          taskGroupMembers[activeGroupIds[i]] = result.value;
        }
      });
    }
  } catch {
    // Non-fatal: custom fields section won't render in task sheet
  }

  // Saved views for TASK entity type (for ViewSelectorClient in TaskListPanel)
  let savedTaskViews: SavedViewResponse[] = [];
  try {
    savedTaskViews = await getViews("TASK");
  } catch {
    // Non-fatal: view selector won't show saved views
  }

  // Inline server action for creating task views
  async function handleCreateTaskView(req: import("@/lib/types").CreateSavedViewRequest) {
    "use server";
    return createSavedViewAction(slug, req);
  }

  // Tags for the Tags section
  let projectTags: TagResponse[] = [];
  let allTags: TagResponse[] = [];
  try {
    const [entityTags, orgTags] = await Promise.all([getMatterTags(id), getTags()]);
    projectTags = entityTags;
    allTags = orgTags;
  } catch {
    // Non-fatal: tags section will show empty state
  }

  // Document templates for the "Generate Document" dropdown (only for users who can manage)
  let projectTemplates: TemplateListResponse[] = [];
  if (canManage) {
    try {
      projectTemplates = await getTemplates(undefined, "PROJECT");
    } catch {
      // Non-fatal: hide generate button if template fetch fails
    }
  }

  return (
    <div className="space-y-6 p-4 lg:p-6">
      <RecordRecentItem href={`/org/${slug}/matters/${id}`} label={project.name} />
      {/* Archived banner (208.11) — above breadcrumb row */}
      {project.status === "ARCHIVED" && (
        <ArchivedProjectBanner slug={slug} projectId={id} canRestore={isAdmin} />
      )}

      {/* Back link + overflow actions row */}
      <div className="flex items-center justify-between">
        <Link
          href={`/org/${slug}/matters`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Matters
        </Link>
        <OverflowActionsMenu
          slug={slug}
          projectId={id}
          projectName={project.name}
          projectStatus={project.status}
          canEdit={canEdit}
          canManage={canManage}
          isAdmin={isAdmin}
          isOwner={isOwner}
          templates={projectTemplates}
          primaryCustomer={
            customers.length > 0
              ? { id: customers[0].id, name: customers[0].name, email: customers[0].email }
              : null
          }
          projectTags={projectTags}
          project={project}
          tasks={tasks}
        />
      </div>

      {/* Matter header card — name, status, work type, client, lifecycle actions */}
      <MatterHeaderCard
        projectId={id}
        projectName={project.name}
        projectStatus={project.status}
        workType={project.workType ?? null}
        referenceNumber={project.referenceNumber ?? null}
        closedAt={project.closedAt ?? null}
        customers={customers.map((c) => ({ id: c.id, name: c.name }))}
        slug={slug}
        isAdmin={isAdmin}
      />

      {/* Tabbed Content */}
      <MatterTabs
        detailsPanel={
          <MatterDetailsTab
            projectId={id}
            description={project.description ?? null}
            priority={project.priority ?? null}
            dueDate={project.dueDate ?? null}
            createdAt={project.createdAt}
            projectStatus={project.status}
            projectTags={projectTags}
            allTags={allTags}
            canEdit={canEdit}
            isAdmin={isAdmin}
            slug={slug}
          />
        }
        fieldsPanel={
          <MatterFieldsTab
            projectId={id}
            customFields={project.customFields ?? {}}
            appliedFieldGroups={project.appliedFieldGroups ?? []}
            fieldDefinitions={projectFieldDefs}
            fieldGroups={projectFieldGroups}
            groupMembers={projectGroupMembers}
            canEdit={canEdit}
            canManage={canManage}
            slug={slug}
          />
        }
        teamPanel={
          <ProjectMembersPanel
            members={members}
            slug={slug}
            projectId={id}
            canManage={canManage}
            isCurrentLead={isCurrentLead}
            currentUserId={currentMemberId ?? ""}
          />
        }
        overviewPanel={
          <OverviewTab
            projectId={id}
            projectName={project.name}
            projectStatus={project.status}
            customerName={customers.length > 0 ? customers[0].name : null}
            customerId={customers.length > 0 ? customers[0].id : null}
            canManage={canManage}
            slug={slug}
            setupStatus={setupStatus}
            setupSteps={setupSteps}
            ficaStatus={ficaStatus}
            retentionClockStartedAt={project.retentionClockStartedAt}
            retentionEndsOn={project.retentionEndsOn}
            trustEnabled={false} // TODO: derive from org module flags
            disbursementsEnabled={disbursementsEnabled}
            projectDueDate={project.dueDate ?? null}
          />
        }
        documentsPanel={
          <DocumentsPanel
            documents={documents}
            projectId={id}
            slug={slug}
            currentMemberId={currentMemberId}
            canManageVisibility={canManage}
            isAiConfigured={isAiConfigured}
            canReviewGates={canReviewGates}
            canExecuteAi={canExecuteAi}
            templates={projectTemplates}
          />
        }
        correspondencePanel={
          <CorrespondenceList items={correspondence} slug={slug} projectId={id} />
        }
        clientsPanel={
          <ProjectCustomersPanel
            customers={customers}
            slug={slug}
            projectId={id}
            canManage={canManage}
          />
        }
        tasksPanel={
          <TaskListPanel
            tasks={tasks}
            slug={slug}
            projectId={id}
            canManage={canManage}
            currentMemberId={currentMemberId}
            isAdmin={isAdmin}
            members={members.map((m) => ({ id: m.memberId, name: m.name, email: m.email }))}
            allTags={allTags}
            fieldDefinitions={taskFieldDefs}
            fieldGroups={taskFieldGroups}
            groupMembers={taskGroupMembers}
            savedViews={savedTaskViews}
            onSave={handleCreateTaskView}
          />
        }
        timePanel={
          <TimeSummaryPanel
            projectId={id}
            initialSummary={timeSummary}
            initialByTask={timeSummaryByTask}
            initialByMember={timeSummaryByMember}
          />
        }
        budgetPanel={
          <BudgetPanel
            slug={slug}
            projectId={id}
            budget={budgetStatus}
            canManage={canManage}
            defaultCurrency={defaultCurrency}
          />
        }
        financialsPanel={
          canManage ? <ProjectFinancialsTab profitability={projectProfitability} /> : undefined
        }
        ratesPanel={
          canManage ? (
            <ProjectRatesTab
              billingRates={projectBillingRates}
              members={members}
              projectId={id}
              slug={slug}
              defaultCurrency={defaultCurrency}
            />
          ) : undefined
        }
        generatedPanel={
          <GeneratedDocumentsList
            entityType="PROJECT"
            entityId={id}
            slug={slug}
            isAdmin={isAdmin}
            customerId={customers.length > 0 ? customers[0].id : undefined}
            canShare={isAdmin || caps.capabilities.includes("PROJECT_MANAGEMENT")}
          />
        }
        requestsPanel={
          customers.length > 0 ? (
            <div className="space-y-6">
              <div className="flex items-center justify-end">
                <CreateRequestDialog
                  slug={slug}
                  customerId={customers[0].id}
                  customerName={customers[0].name}
                  projectId={id}
                  projectName={project.name}
                />
              </div>
              <RequestList requests={projectRequests} slug={slug} showCustomer={false} />
            </div>
          ) : undefined
        }
        customerCommentsPanel={
          customers && customers.length > 0 ? (
            <ProjectCommentsSection projectId={id} orgSlug={slug} />
          ) : undefined
        }
        courtDatesPanel={
          <ProjectCourtDatesTab projectId={id} slug={slug} canManageLegal={canManageLegal} />
        }
        adversePartiesPanel={<ProjectAdversePartiesTab projectId={id} slug={slug} />}
        disbursementsPanel={
          <ProjectDisbursementsTab projectId={id} slug={slug} canManage={canManage} />
        }
        statementsPanel={
          <ProjectStatementsTab projectId={id} slug={slug} projectName={project.name} />
        }
        trustPanel={
          customers.length > 0 ? (
            <TrustBalanceCard
              customerId={customers[0].id}
              projectId={id}
              slug={slug}
              showQuickActions={canManage}
            />
          ) : undefined
        }
        activityPanel={<ActivityFeed projectId={id} orgSlug={slug} />}
        auditPanel={<ProjectAuditTab projectId={id} />}
      />

      {/* Epic 508B: Closure history (only on CLOSED matters). Per-row audit
          timelines surface the matter.closure.override_used event from 508A. */}
      {project.status === "CLOSED" && <ClosureHistorySection projectId={id} />}
    </div>
  );
}
