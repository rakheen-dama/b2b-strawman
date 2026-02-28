import { getAuthContext } from "@/lib/auth";
import { api, handleApiError } from "@/lib/api";
import type {
  Project,
  Customer,
  Document,
  ProjectMember,
  Task,
  ProjectTimeSummary,
  MemberTimeSummary,
  TaskTimeSummary,
  BudgetStatusResponse,
} from "@/lib/types";
import { PageHeader } from "@/components/layout/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { formatDate, formatLocalDate, isOverdue } from "@/lib/format";
import { ArrowLeft, Calendar, AlertTriangle, Pencil } from "lucide-react";
import Link from "next/link";
import { cn } from "@/lib/utils";
import { ProjectDetailClient } from "@/components/projects/project-detail-client";
import {
  updateProject,
  completeProject,
  archiveProject,
  reopenProject,
} from "../actions";
import { createTask, updateTask, deleteTask } from "./task-actions";
import { createTimeEntry } from "./time-entry-actions";

export default async function ProjectDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole, userId } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let project: Project;
  try {
    project = await api.get<Project>(`/api/projects/${id}`);
  } catch (error) {
    handleApiError(error);
    // handleApiError throws, but TypeScript doesn't know that
    throw error;
  }

  const canEdit = isAdmin || project.projectRole === "lead";

  // Parallel fetches for all tab data
  const [
    documentsResult,
    membersResult,
    tasksResult,
    customersResult,
    timeSummaryResult,
    byTaskResult,
    byMemberResult,
    budgetResult,
  ] = await Promise.allSettled([
    api.get<Document[]>(`/api/projects/${id}/documents`),
    api.get<ProjectMember[]>(`/api/projects/${id}/members`),
    api.get<Task[]>(`/api/projects/${id}/tasks`),
    api.get<Customer[]>(`/api/projects/${id}/customers`),
    api.get<ProjectTimeSummary>(`/api/projects/${id}/time-summary`),
    api.get<TaskTimeSummary[]>(`/api/projects/${id}/time-summary/by-task`),
    api
      .get<MemberTimeSummary[]>(`/api/projects/${id}/time-summary/by-member`)
      .catch(() => null),
    api
      .get<BudgetStatusResponse>(`/api/projects/${id}/budget`)
      .catch(() => null),
  ]);

  const documents =
    documentsResult.status === "fulfilled" ? documentsResult.value : [];
  const members =
    membersResult.status === "fulfilled" ? membersResult.value : [];
  const tasks = tasksResult.status === "fulfilled" ? tasksResult.value : [];
  const customers =
    customersResult.status === "fulfilled" ? customersResult.value : [];
  const timeSummary =
    timeSummaryResult.status === "fulfilled"
      ? timeSummaryResult.value
      : {
          billableMinutes: 0,
          nonBillableMinutes: 0,
          totalMinutes: 0,
          contributorCount: 0,
          entryCount: 0,
        };
  const timeSummaryByTask =
    byTaskResult.status === "fulfilled" ? byTaskResult.value : [];
  const timeSummaryByMember =
    byMemberResult.status === "fulfilled" ? byMemberResult.value : null;
  const budgetStatus =
    budgetResult.status === "fulfilled" ? budgetResult.value : null;

  const doneTasks = tasks.filter((t) => t.status === "DONE").length;

  return (
    <div className="space-y-6">
      {/* Back link */}
      <Link
        href={`/org/${slug}/projects`}
        className="inline-flex items-center text-sm text-slate-500 transition-colors hover:text-slate-700"
      >
        <ArrowLeft className="mr-1.5 size-4" />
        Back to Projects
      </Link>

      {/* Project header */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
              {project.name}
            </h1>
            <StatusBadge status={project.status} />
            {project.projectRole && (
              <StatusBadge status={project.projectRole} />
            )}
          </div>
          {project.description && (
            <p className="mt-2 text-sm text-slate-600">
              {project.description}
            </p>
          )}
          <div className="mt-3 flex flex-wrap items-center gap-3 text-sm text-slate-500">
            {project.dueDate && (
              <span
                className={cn(
                  "inline-flex items-center gap-1",
                  project.status === "ACTIVE" && isOverdue(project.dueDate)
                    ? "font-medium text-red-600"
                    : ""
                )}
              >
                {project.status === "ACTIVE" &&
                isOverdue(project.dueDate) ? (
                  <AlertTriangle className="size-3.5" />
                ) : (
                  <Calendar className="size-3.5" />
                )}
                Due {formatLocalDate(project.dueDate)}
              </span>
            )}
            {customers.length > 0 && (
              <span>
                Customer:{" "}
                <Link
                  href={`/org/${slug}/customers/${customers[0].id}`}
                  className="text-teal-600 hover:text-teal-700 hover:underline"
                >
                  {customers[0].name}
                </Link>
              </span>
            )}
            <span>Created {formatDate(project.createdAt)}</span>
            <span>
              {documents.length}{" "}
              {documents.length === 1 ? "doc" : "docs"}
            </span>
            <span>
              {members.length}{" "}
              {members.length === 1 ? "member" : "members"}
            </span>
            <span>
              {doneTasks}/{tasks.length} tasks done
            </span>
          </div>
        </div>
      </div>

      {/* Tabbed content */}
      <ProjectDetailClient
        slug={slug}
        projectId={id}
        project={project}
        tasks={tasks}
        members={members}
        customers={customers}
        documents={documents}
        timeSummary={timeSummary}
        timeSummaryByTask={timeSummaryByTask}
        timeSummaryByMember={timeSummaryByMember}
        budgetStatus={budgetStatus}
        canEdit={canEdit}
        isAdmin={isAdmin}
        onCreateTask={createTask}
        onCreateTimeEntry={createTimeEntry}
        onUpdateProject={updateProject}
      />
    </div>
  );
}
