import { auth, currentUser } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import type { OrgMember, Project, Customer, Document, ProjectMember, ProjectRole, Task, ProjectTimeSummary, MemberTimeSummary, TaskTimeSummary, BillingRate, OrgSettings, BudgetStatusResponse, ProjectProfitabilityResponse } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { EditProjectDialog } from "@/components/projects/edit-project-dialog";
import { DeleteProjectDialog } from "@/components/projects/delete-project-dialog";
import { DocumentsPanel } from "@/components/documents/documents-panel";
import { ProjectMembersPanel } from "@/components/projects/project-members-panel";
import { TaskListPanel } from "@/components/tasks/task-list-panel";
import { TimeSummaryPanel } from "@/components/projects/time-summary-panel";
import { ActivityFeed } from "@/components/activity/activity-feed";
import { ProjectCustomersPanel } from "@/components/projects/project-customers-panel";
import { ProjectTabs } from "@/components/projects/project-tabs";
import { ProjectRatesTab } from "@/components/rates/project-rates-tab";
import { BudgetPanel } from "@/components/budget/budget-panel";
import { ProjectFinancialsTab } from "@/components/profitability/project-financials-tab";
import { OverviewTab } from "@/components/projects/overview-tab";
import { formatDate } from "@/lib/format";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import Link from "next/link";

const ROLE_BADGE: Record<ProjectRole, { label: string; variant: "lead" | "member" }> = {
  lead: { label: "Lead", variant: "lead" },
  member: { label: "Member", variant: "member" },
};

export default async function ProjectDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole, userId } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";
  const isOwner = orgRole === "org:owner";

  let project: Project;
  try {
    project = await api.get<Project>(`/api/projects/${id}`);
  } catch (error) {
    handleApiError(error);
  }

  const canEdit = isAdmin || project.projectRole === "lead";
  const isCurrentLead = project.projectRole === "lead";
  const canManage = isAdmin || isCurrentLead;

  let documents: Document[] = [];
  try {
    documents = await api.get<Document[]>(`/api/projects/${id}/documents`);
  } catch {
    // Non-fatal: show empty documents list if fetch fails
  }

  let members: ProjectMember[] = [];
  try {
    members = await api.get<ProjectMember[]>(`/api/projects/${id}/members`);
  } catch {
    // Non-fatal: show empty members list if fetch fails
  }

  let tasks: Task[] = [];
  try {
    tasks = await api.get<Task[]>(`/api/projects/${id}/tasks`);
  } catch {
    // Non-fatal: show empty tasks list if fetch fails
  }

  let customers: Customer[] = [];
  try {
    customers = await api.get<Customer[]>(`/api/projects/${id}/customers`);
  } catch {
    // Non-fatal: show empty customers list if fetch fails
  }

  // Time summary data for the "Time" tab
  let timeSummary: ProjectTimeSummary = {
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
      api.get<ProjectTimeSummary>(`/api/projects/${id}/time-summary`),
      api.get<TaskTimeSummary[]>(`/api/projects/${id}/time-summary/by-task`),
      api
        .get<MemberTimeSummary[]>(`/api/projects/${id}/time-summary/by-member`)
        .catch(() => null),
    ]);
    timeSummary = summaryRes;
    timeSummaryByTask = byTaskRes;
    timeSummaryByMember = byMemberRes;
  } catch {
    // Non-fatal: show empty time summary if fetch fails
  }

  // Billing rates + settings for the "Rates" tab (only fetched for users who can manage)
  let projectBillingRates: BillingRate[] = [];
  let defaultCurrency = "USD";
  if (canManage) {
    try {
      const [ratesRes, settingsRes] = await Promise.all([
        api.get<BillingRate[]>(`/api/billing-rates?projectId=${id}`),
        api.get<OrgSettings>("/api/settings").catch(() => null),
      ]);
      projectBillingRates = ratesRes;
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
    budgetStatus = await api.get<BudgetStatusResponse>(
      `/api/projects/${id}/budget`
    );
  } catch {
    // Non-fatal: 404 means no budget set, other errors show empty state
  }

  // Project profitability for the "Financials" tab (lead/admin/owner)
  let projectProfitability: ProjectProfitabilityResponse | null = null;
  if (canManage) {
    try {
      projectProfitability = await api.get<ProjectProfitabilityResponse>(
        `/api/projects/${id}/profitability`
      );
    } catch {
      // Non-fatal: show empty state in financials tab
    }
  }

  // Resolve current user's backend member ID for "My Tasks" filter and claim/release actions.
  // Match Clerk user email against org members list from the backend.
  let currentMemberId: string | null = null;
  try {
    const [user, orgMembers] = await Promise.all([
      currentUser(),
      api.get<OrgMember[]>("/api/members"),
    ]);
    const email = user?.primaryEmailAddress?.emailAddress;
    if (email) {
      const match = orgMembers.find((m) => m.email === email);
      if (match) currentMemberId = match.id;
    }
  } catch {
    // Non-fatal: "My Tasks" filter won't work without member ID
  }

  const roleBadge = project.projectRole ? ROLE_BADGE[project.projectRole] : null;

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/projects`}
          className="inline-flex items-center text-sm text-olive-600 transition-colors hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Projects
        </Link>
      </div>

      {/* Project Header (33.6) */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-2xl text-olive-950 dark:text-olive-50">
              {project.name}
            </h1>
            {roleBadge && <Badge variant={roleBadge.variant}>{roleBadge.label}</Badge>}
          </div>
          {project.description ? (
            <p className="mt-2 text-olive-600 dark:text-olive-400">{project.description}</p>
          ) : (
            <p className="mt-2 text-sm italic text-olive-400 dark:text-olive-600">
              No description
            </p>
          )}
          <p className="mt-3 text-sm text-olive-400 dark:text-olive-600">
            Created {formatDate(project.createdAt)} &middot; {documents.length}{" "}
            {documents.length === 1 ? "document" : "documents"} &middot; {members.length}{" "}
            {members.length === 1 ? "member" : "members"} &middot; {tasks.length}{" "}
            {tasks.length === 1 ? "task" : "tasks"}
          </p>
        </div>

        {(canEdit || isOwner) && (
          <div className="flex shrink-0 gap-2">
            {canEdit && (
              <EditProjectDialog project={project} slug={slug}>
                <Button variant="outline" size="sm">
                  <Pencil className="mr-1.5 size-4" />
                  Edit
                </Button>
              </EditProjectDialog>
            )}
            {isOwner && (
              <DeleteProjectDialog slug={slug} projectId={project.id} projectName={project.name}>
                <Button variant="ghost" size="sm" className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300">
                  <Trash2 className="mr-1.5 size-4" />
                  Delete
                </Button>
              </DeleteProjectDialog>
            )}
          </div>
        )}
      </div>

      {/* Tabbed Content (33.7) */}
      <ProjectTabs
        overviewPanel={
          <OverviewTab
            projectId={id}
            projectName={project.name}
            customerName={customers.length > 0 ? customers[0].name : null}
            canManage={canManage}
            tasks={tasks}
            slug={slug}
          />
        }
        documentsPanel={
          <DocumentsPanel
            documents={documents}
            projectId={id}
            slug={slug}
            currentMemberId={currentMemberId}
            canManageVisibility={canManage}
          />
        }
        customersPanel={
          <ProjectCustomersPanel
            customers={customers}
            slug={slug}
            projectId={id}
            canManage={canManage}
          />
        }
        membersPanel={
          <ProjectMembersPanel
            members={members}
            slug={slug}
            projectId={id}
            canManage={canManage}
            isCurrentLead={isCurrentLead}
            currentUserId={userId ?? ""}
          />
        }
        tasksPanel={
          <TaskListPanel
            tasks={tasks}
            slug={slug}
            projectId={id}
            canManage={canManage}
            currentMemberId={currentMemberId}
            orgRole={orgRole}
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
          canManage ? (
            <ProjectFinancialsTab
              profitability={projectProfitability}
            />
          ) : undefined
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
        activityPanel={
          <ActivityFeed projectId={id} orgSlug={slug} />
        }
      />
    </div>
  );
}
