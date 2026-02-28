import { getAuthContext } from "@/lib/auth";
import { api, handleApiError } from "@/lib/api";
import type {
  Project,
  Customer,
  LightweightBudgetStatus,
} from "@/lib/types";
import { PageHeader } from "@/components/layout/page-header";
import { ProjectListClient } from "@/components/projects/project-list-client";
import { createProject, fetchActiveCustomers } from "./actions";

export default async function ProjectsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let projects: Project[] = [];
  try {
    projects = await api.get<Project[]>("/api/projects");
  } catch (error) {
    handleApiError(error);
  }

  // Fetch customers for project creation dialog
  let customers: Customer[] = [];
  try {
    customers = await fetchActiveCustomers();
  } catch {
    // Non-fatal
  }

  // Fetch budget status for each project (admin-only, non-fatal)
  const budgetStatuses = new Map<string, LightweightBudgetStatus>();
  if (isAdmin && projects.length > 0) {
    const results = await Promise.allSettled(
      projects.map((p) =>
        api.get<LightweightBudgetStatus>(
          `/api/projects/${p.id}/budget/status`
        )
      )
    );
    results.forEach((result, i) => {
      if (result.status === "fulfilled" && result.value) {
        budgetStatuses.set(projects[i].id, result.value);
      }
    });
  }

  // Fetch customer names for display. Projects have customerId on them,
  // but we also need the customer's name. Build a map from linked customers.
  const customerNameMap = new Map<string, string>();
  if (projects.length > 0) {
    try {
      const customerResults = await Promise.allSettled(
        projects.map((p) =>
          api.get<Customer[]>(`/api/projects/${p.id}/customers`)
        )
      );
      customerResults.forEach((result, i) => {
        if (result.status === "fulfilled" && result.value.length > 0) {
          customerNameMap.set(projects[i].id, result.value[0].name);
        }
      });
    } catch {
      // Non-fatal
    }
  }

  // Build enriched rows for the table
  const projectRows = projects.map((p) => ({
    ...p,
    customerName: customerNameMap.get(p.id) ?? null,
    budgetStatus: budgetStatuses.get(p.id) ?? null,
    tasksDone: 0,
    tasksTotal: 0,
  }));

  return (
    <div className="space-y-6">
      <PageHeader
        title="Projects"
        count={projects.length}
        description="Manage all your organization's projects"
      />
      <ProjectListClient
        projects={projectRows}
        customers={customers}
        slug={slug}
        onCreateProject={createProject}
      />
    </div>
  );
}
