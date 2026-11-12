import { getAuthContext } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { handleApiError } from "@/lib/api";
import { getMatter } from "@/lib/api/matters";
import { isModuleEnabledServer } from "@/lib/api/settings";
import type { CreateSavedViewRequest, Matter } from "@/lib/types";
import { MatterHeaderCard } from "@/components/projects/matter-header-card";
import { MatterDetailsTab } from "@/components/projects/matter-details-tab";
import { MatterFieldsTab } from "@/components/projects/matter-fields-tab";
import { DocumentsPanel } from "@/components/documents/documents-panel";
import { CorrespondenceList } from "@/components/correspondence/correspondence-list";
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
import { CommentSectionClient } from "@/components/comments/comment-section-client";
import { RequestList } from "@/components/information-requests/request-list";
import { CreateRequestDialog } from "@/components/information-requests/create-request-dialog";
import { ArchivedProjectBanner } from "@/components/projects/archived-project-banner";
import { OverflowActionsMenu } from "@/components/projects/overflow-actions-menu";
import { ProjectCourtDatesTab } from "@/components/legal/project-court-dates-tab";
import { ProjectAdversePartiesTab } from "@/components/legal/project-adverse-parties-tab";
import { ProjectDisbursementsTab } from "@/components/legal/project-disbursements-tab";
import { ProjectStatementsTab } from "@/components/legal/project-statements-tab";
import { TrustBalanceCard } from "@/components/trust/TrustBalanceCard";
import { RecordRecentItem } from "@/components/record-recent-item";
import { createSavedViewAction } from "./view-actions";
import { buildMatterHeroTiles, loadMatterHeroData, upcomingDeadlineRows } from "./hero-tiles";
import { loadMatterPageData } from "./matter-page-data";
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

  // Module flags gate hero reads and the Overview trust card — read once, alongside the matter.
  // Never rejects (isModuleEnabledServer falls back to false).
  const modulesPromise = Promise.all([
    isModuleEnabledServer("trust_accounting"),
    isModuleEnabledServer("court_calendar"),
  ]);
  let project: Matter;
  try {
    project = await getMatter(id);
  } catch (error) {
    handleApiError(error);
  }
  const [trustEnabled, courtCalendarEnabled] = await modulesPromise;

  const canEdit = isAdmin || project.matterRole === "lead";
  const isCurrentLead = project.matterRole === "lead";
  const canManage = isAdmin || isCurrentLead;

  // Loaders (641B, §15.3.5): the tab-panel reads (./matter-page-data, incl. the budget the Budget
  // and Overview tabs share) and the hero reads (./hero-tiles — trust, outstanding, unbilled WIP,
  // court dates, which the Overview tab shares). Each read once; independent → in parallel.
  const [data, hero] = await Promise.all([
    loadMatterPageData({ id, project, caps, canManage }),
    loadMatterHeroData(id, caps, { trustEnabled, courtCalendarEnabled }),
  ]);
  const {
    budget: budgetStatus,
    setupStatus,
    setupSteps,
    canExecuteAi,
    canReviewGates,
    isAiConfigured,
    documents,
    correspondence,
    members,
    tasks,
    customers,
    projectRequests,
    ficaStatus,
    timeSummary,
    timeSummaryByTask,
    timeSummaryByMember,
    projectBillingRates,
    defaultCurrency,
    projectProfitability,
    currentMemberId,
    projectFieldDefs,
    projectFieldGroups,
    projectGroupMembers,
    headerFields,
    taskFieldDefs,
    taskFieldGroups,
    taskGroupMembers,
    savedTaskViews,
    projectTags,
    allTags,
    projectTemplates,
  } = data;
  const { outstandingSummary, trustBalance: matterTrustBalance } = hero;

  // Hero subtitle fee earner: the matter's earliest lead — the matters list's "Fee earner" rule.
  const feeEarner = members
    .filter((m) => m.matterRole === "lead")
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt))[0];

  // Inline server action for creating task views
  async function handleCreateTaskView(req: CreateSavedViewRequest) {
    "use server";
    return createSavedViewAction(slug, req);
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
          className="text-muted-foreground hover:text-foreground inline-flex items-center text-sm transition-colors"
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

      {/* Matter hero (DetailHero, 641B) — name, status, client, lifecycle actions, KPI tiles */}
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
        headerFields={headerFields}
        feeEarnerName={feeEarner?.name ?? null}
        openedAt={project.createdAt}
        tiles={buildMatterHeroTiles(hero)}
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
            trustEnabled={trustEnabled}
            outstandingSummary={outstandingSummary}
            trustBalance={matterTrustBalance}
            projectDueDate={project.dueDate ?? null}
            budget={budgetStatus}
            upcomingDeadlines={upcomingDeadlineRows(hero)}
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
        commentsPanel={
          // Matter (PROJECT-target) comments — mounted unconditionally so a comment notification's
          // ?tab=comments&commentId= deep link always has a landing surface (634E, rulings §9.5).
          <CommentSectionClient
            projectId={id}
            entityType="PROJECT"
            entityId={id}
            orgSlug={slug}
            currentMemberId={currentMemberId ?? ""}
            canManageVisibility={canManage}
          />
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
