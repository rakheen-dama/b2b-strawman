import { Suspense } from "react";
import { getAuthContext, hasPlan } from "@/lib/auth";
import { api, handleApiError, getFieldDefinitions, getViews, getTags } from "@/lib/api";
import { fetchRetainerSummary } from "@/lib/api/retainers";
import type { RetainerSummaryResponse } from "@/lib/types";
import { getProjectTemplates } from "@/lib/api/templates";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { Project, ProjectRole, LightweightBudgetStatus, FieldDefinitionResponse, SavedViewResponse, TagResponse, OrgMember, Customer } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { BudgetStatusDot } from "@/components/projects/budget-status-dot";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { NewFromTemplateWrapper } from "@/components/templates/NewFromTemplateWrapper";
import { CustomFieldBadges } from "@/components/field-definitions/CustomFieldBadges";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import { createSavedViewAction } from "./view-actions";
import { formatDate } from "@/lib/format";
import { Clock, FileText, FolderOpen, Users } from "lucide-react";
import Link from "next/link";

const ROLE_BADGE: Record<ProjectRole, { label: string; variant: "lead" | "member" }> = {
  lead: { label: "Lead", variant: "lead" },
  member: { label: "Member", variant: "member" },
};

export default async function ProjectsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";
  const isPro = await hasPlan("pro");

  const currentViewId = typeof resolvedSearchParams.view === "string" ? resolvedSearchParams.view : null;

  // Fetch saved views for project entity type
  let views: SavedViewResponse[] = [];
  try {
    views = await getViews("PROJECT");
  } catch {
    // Non-fatal: view selector won't show saved views
  }

  // Build query string with view filters
  let projectsEndpoint = "/api/projects";
  if (currentViewId) {
    projectsEndpoint = `/api/projects?view=${currentViewId}`;
  }

  let projects: Project[] = [];
  try {
    projects = await api.get<Project[]>(projectsEndpoint);
  } catch (error) {
    handleApiError(error);
  }

  // Fetch field definitions for custom field badges on project cards
  let projectFieldDefs: FieldDefinitionResponse[] = [];
  try {
    projectFieldDefs = await getFieldDefinitions("PROJECT");
  } catch {
    // Non-fatal: custom field badges won't render
  }

  // Fetch tags for filter UI
  let allTags: TagResponse[] = [];
  try {
    allTags = await getTags();
  } catch {
    // Non-fatal
  }

  // Fetch active project templates for "New from Template" button (admin/owner only)
  let activeTemplates: ProjectTemplateResponse[] = [];
  let orgMembers: OrgMember[] = [];
  let allCustomers: Customer[] = [];

  if (isAdmin) {
    try {
      const allTemplates = await getProjectTemplates();
      activeTemplates = allTemplates.filter((t) => t.active);
    } catch {
      // Non-fatal: hide "New from Template" button
    }

    // Only fetch members + customers if there are active templates to instantiate
    if (activeTemplates.length > 0) {
      const [membersResult, customersResult] = await Promise.allSettled([
        api.get<OrgMember[]>("/api/members"),
        api.get<Customer[]>("/api/customers"),
      ]);
      if (membersResult.status === "fulfilled") orgMembers = membersResult.value;
      if (customersResult.status === "fulfilled") allCustomers = customersResult.value;
    }
  }

  // Fetch budget status for each project (admin-only, non-fatal — 404 means no budget)
  const budgetStatuses = new Map<string, LightweightBudgetStatus>();
  if (isAdmin && projects.length > 0) {
    const results = await Promise.allSettled(
      projects.map((p) =>
        api.get<LightweightBudgetStatus>(`/api/projects/${p.id}/budget/status`),
      ),
    );
    results.forEach((result, i) => {
      if (result.status === "fulfilled" && result.value) {
        budgetStatuses.set(projects[i].id, result.value);
      }
    });
  }

  // Fetch project-customer mappings and retainer summaries for badge display (non-fatal).
  // Customer IDs are deduplicated before fetching retainer summaries, so this is
  // O(projects) for customer lookups + O(unique customers) for retainer summaries —
  // multiple projects sharing the same customer only trigger one retainer API call.
  const projectCustomerMap = new Map<string, string>();
  const retainerSummaryMap = new Map<string, RetainerSummaryResponse>();
  if (projects.length > 0) {
    try {
      const customerResults = await Promise.allSettled(
        projects.map((p) => api.get<Customer[]>(`/api/projects/${p.id}/customers`)),
      );
      customerResults.forEach((result, i) => {
        if (result.status === "fulfilled" && result.value.length > 0) {
          projectCustomerMap.set(projects[i].id, result.value[0].id);
        }
      });

      const uniqueCustomerIds = [...new Set(projectCustomerMap.values())];
      if (uniqueCustomerIds.length > 0) {
        const summaryResults = await Promise.allSettled(
          uniqueCustomerIds.map((cid) => fetchRetainerSummary(cid)),
        );
        summaryResults.forEach((result, i) => {
          if (result.status === "fulfilled") {
            retainerSummaryMap.set(uniqueCustomerIds[i], result.value);
          }
        });
      }
    } catch {
      // Non-fatal: retainer badges won't render
    }
  }

  async function handleCreateView(req: import("@/lib/types").CreateSavedViewRequest) {
    "use server";
    return createSavedViewAction(slug, req);
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Projects</h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {projects.length} {projects.length === 1 ? "project" : "projects"}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <NewFromTemplateWrapper
            slug={slug}
            templates={activeTemplates}
            orgMembers={orgMembers}
            customers={allCustomers}
          />
          <CreateProjectDialog slug={slug} />
        </div>
      </div>

      {/* Upgrade Prompt (Starter only) */}
      {!isPro && (
        <div data-testid="upgrade-prompt" className="flex items-start gap-4 rounded-lg border-l-4 border-teal-500 bg-slate-100 p-4 dark:bg-slate-900">
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
              Upgrade to Pro for dedicated infrastructure and schema isolation
            </p>
            <Link
              href={`/org/${slug}/settings/billing`}
              className="mt-1 inline-block text-sm text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
            >
              Learn more
            </Link>
          </div>
        </div>
      )}

      {/* Saved View Selector */}
      <Suspense fallback={null}>
        <ViewSelectorClient
          entityType="PROJECT"
          views={views}
          canCreate={true}
          canCreateShared={isAdmin}
          slug={slug}
          allTags={allTags}
          fieldDefinitions={projectFieldDefs}
          onSave={handleCreateView}
        />
      </Suspense>

      {/* Projects Grid or Empty State */}
      {projects.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <FolderOpen className="size-16 text-slate-300 dark:text-slate-700" />
          <h2 className="font-display mt-6 text-xl text-slate-900 dark:text-slate-100">
            No projects yet
          </h2>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            {isAdmin
              ? "Create your first project to get started."
              : "You\u2019re not on any projects yet."}
          </p>
          <div className="mt-6">
            <CreateProjectDialog slug={slug} />
          </div>
        </div>
      ) : (
        <div className="grid auto-rows-fr grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {projects.map((project) => {
            const roleBadge = project.projectRole ? ROLE_BADGE[project.projectRole] : null;
            const budgetStatus = budgetStatuses.get(project.id);
            const customerId = projectCustomerMap.get(project.id);
            const retainerSummary = customerId ? retainerSummaryMap.get(customerId) : undefined;
            const hasRetainer = retainerSummary?.hasActiveRetainer === true;
            return (
              <Link
                key={project.id}
                href={`/org/${slug}/projects/${project.id}`}
                className="group rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-600"
              >
                <div className="rounded-lg border border-slate-200 bg-white p-6 transition-all duration-150 hover:border-slate-300 hover:shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:hover:border-slate-700">
                  {/* Top: name + role badge + budget indicator */}
                  <div className="flex items-center gap-2">
                    <h3 className="line-clamp-1 font-semibold text-slate-950 dark:text-slate-50">
                      {project.name}
                    </h3>
                    {roleBadge && (
                      <Badge variant={roleBadge.variant} className="shrink-0">
                        {roleBadge.label}
                      </Badge>
                    )}
                    {budgetStatus?.overallStatus && (
                      <BudgetStatusDot status={budgetStatus.overallStatus} />
                    )}
                    {hasRetainer && (
                      <span
                        className="inline-flex items-center gap-1 rounded-full bg-teal-50 px-2 py-0.5 text-xs font-medium text-teal-700 dark:bg-teal-900/30 dark:text-teal-400"
                        title="Customer has an active retainer"
                        data-testid="retainer-badge"
                      >
                        <Clock className="size-3" />
                        Retainer
                      </span>
                    )}
                  </div>

                  {/* Middle: description */}
                  {project.description ? (
                    <p className="mt-2 line-clamp-2 text-sm text-slate-600 dark:text-slate-400">
                      {project.description}
                    </p>
                  ) : (
                    <p className="mt-2 text-sm italic text-slate-400 dark:text-slate-600">
                      No description
                    </p>
                  )}

                  {/* Bottom: meta icons */}
                  <div className="mt-4 flex items-center gap-4 text-sm text-slate-400 dark:text-slate-600">
                    <span className="inline-flex items-center gap-1">
                      <FileText className="size-3.5" />
                      &mdash;
                    </span>
                    <span className="inline-flex items-center gap-1">
                      <Users className="size-3.5" />
                      &mdash;
                    </span>
                    <span>{formatDate(project.createdAt)}</span>
                  </div>

                  {/* Tag badges */}
                  {project.tags && project.tags.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1">
                      {project.tags.slice(0, 3).map((tag) => (
                        <Badge
                          key={tag.id}
                          variant="outline"
                          className="text-xs"
                          style={
                            tag.color
                              ? { borderColor: tag.color, color: tag.color }
                              : undefined
                          }
                        >
                          {tag.name}
                        </Badge>
                      ))}
                      {project.tags.length > 3 && (
                        <Badge variant="neutral" className="text-xs">
                          +{project.tags.length - 3}
                        </Badge>
                      )}
                    </div>
                  )}

                  {/* Custom field badges */}
                  {project.customFields && Object.keys(project.customFields).length > 0 && (
                    <CustomFieldBadges
                      customFields={project.customFields}
                      fieldDefinitions={projectFieldDefs}
                      maxFields={3}
                    />
                  )}
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
