import { auth } from "@clerk/nextjs/server";
import { api, handleApiError, getFieldDefinitions } from "@/lib/api";
import type { Project, ProjectRole, LightweightBudgetStatus, FieldDefinitionResponse } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { BudgetStatusDot } from "@/components/projects/budget-status-dot";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { CustomFieldBadges } from "@/components/field-definitions/CustomFieldBadges";
import { formatDate } from "@/lib/format";
import { FileText, FolderOpen, Users } from "lucide-react";
import Link from "next/link";

const ROLE_BADGE: Record<ProjectRole, { label: string; variant: "lead" | "member" }> = {
  lead: { label: "Lead", variant: "lead" },
  member: { label: "Member", variant: "member" },
};

export default async function ProjectsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const { orgRole, has } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";
  const isPro = has?.({ plan: "pro" }) ?? false;

  let projects: Project[] = [];
  try {
    projects = await api.get<Project[]>("/api/projects");
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

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">Projects</h1>
          <p className="mt-1 text-sm text-olive-600 dark:text-olive-400">
            {projects.length} {projects.length === 1 ? "project" : "projects"}
          </p>
        </div>
        <CreateProjectDialog slug={slug} />
      </div>

      {/* Upgrade Prompt (Starter only) */}
      {!isPro && (
        <div data-testid="upgrade-prompt" className="flex items-start gap-4 rounded-lg border-l-4 border-indigo-500 bg-olive-100 p-4 dark:bg-olive-900">
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-olive-900 dark:text-olive-100">
              Upgrade to Pro for dedicated infrastructure and schema isolation
            </p>
            <Link
              href={`/org/${slug}/settings/billing`}
              className="mt-1 inline-block text-sm text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300"
            >
              Learn more
            </Link>
          </div>
        </div>
      )}

      {/* Projects Grid or Empty State */}
      {projects.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <FolderOpen className="size-16 text-olive-300 dark:text-olive-700" />
          <h2 className="font-display mt-6 text-xl text-olive-900 dark:text-olive-100">
            No projects yet
          </h2>
          <p className="mt-2 text-sm text-olive-600 dark:text-olive-400">
            {isAdmin
              ? "Create your first project to get started."
              : "You\u2019re not on any projects yet."}
          </p>
          <div className="mt-6">
            <CreateProjectDialog slug={slug} />
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {projects.map((project) => {
            const roleBadge = project.projectRole ? ROLE_BADGE[project.projectRole] : null;
            const budgetStatus = budgetStatuses.get(project.id);
            return (
              <Link
                key={project.id}
                href={`/org/${slug}/projects/${project.id}`}
                className="group rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-olive-600"
              >
                <div className="rounded-lg border border-olive-200 bg-white p-6 transition-all duration-150 hover:border-olive-300 hover:shadow-sm dark:border-olive-800 dark:bg-olive-950 dark:hover:border-olive-700">
                  {/* Top: name + role badge + budget indicator */}
                  <div className="flex items-center gap-2">
                    <h3 className="line-clamp-1 font-semibold text-olive-950 dark:text-olive-50">
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
                  </div>

                  {/* Middle: description */}
                  {project.description ? (
                    <p className="mt-2 line-clamp-2 text-sm text-olive-600 dark:text-olive-400">
                      {project.description}
                    </p>
                  ) : (
                    <p className="mt-2 text-sm italic text-olive-400 dark:text-olive-600">
                      No description
                    </p>
                  )}

                  {/* Bottom: meta icons */}
                  <div className="mt-4 flex items-center gap-4 text-sm text-olive-400 dark:text-olive-600">
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
