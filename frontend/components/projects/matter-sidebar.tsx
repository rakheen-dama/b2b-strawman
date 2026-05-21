"use client";

import Link from "next/link";
import { AlertTriangle, Calendar } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { ExpandableText } from "@/components/ui/expandable-text";
import { CustomFieldSection } from "@/components/field-definitions/CustomFieldSection";
import { FieldGroupSelector } from "@/components/field-definitions/FieldGroupSelector";
import { TagInput } from "@/components/tags/TagInput";
import { TerminologyText } from "@/components/terminology-text";
import { PROJECT_STATUS_BADGE } from "@/lib/constants/project-status";
import { ProjectLifecycleActions } from "@/components/projects/project-lifecycle-actions";
import { MatterClosureAction } from "@/components/projects/matter-closure-action";
import { MatterReopenAction } from "@/components/projects/matter-reopen-action";
import { formatDate, formatLocalDate, isOverdue } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { MatterSidebarProps } from "@/lib/types/matter-sidebar";

export function MatterSidebar({
  project,
  customers,
  slug,
  canEdit,
  canManage,
  isAdmin,
  isOwner,
  fieldDefinitions,
  fieldGroups,
  groupMembers,
  projectTags,
  allTags,
}: MatterSidebarProps) {
  const statusBadge = PROJECT_STATUS_BADGE[project.status];

  return (
    <div data-testid="matter-sidebar" className="flex h-full flex-col">
      {/* (A) Matter Identity */}
      <div className="space-y-2 p-4">
        <h2 className="font-display line-clamp-3 text-lg font-semibold text-slate-950 dark:text-slate-50">
          {project.name}
        </h2>
        <Badge variant={statusBadge.variant} data-testid="project-status-badge">
          {statusBadge.label}
        </Badge>
        {project.status === "CLOSED" && project.closedAt && (
          <p className="text-sm text-slate-500 dark:text-slate-400" data-testid="project-closed-at">
            Closed on {formatDate(project.closedAt)}
          </p>
        )}
        <ExpandableText
          text={project.description}
          lineClamp={2}
          className="text-muted-foreground text-sm"
        />
      </div>

      {/* (B) Key Metadata */}
      <div className="space-y-2 border-t border-slate-200 p-4 dark:border-slate-800">
        {/* Client */}
        <div className="flex items-start gap-2 text-sm">
          <span className="w-20 shrink-0 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Client
          </span>
          <span className="min-w-0 text-slate-700 dark:text-slate-300">
            {customers.length > 0 ? (
              <Link
                href={`/org/${slug}/customers/${customers[0].id}`}
                className="text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
              >
                {customers[0].name}
              </Link>
            ) : (
              <TerminologyText template="Internal {Project}" />
            )}
          </span>
        </div>

        {/* Reference Number */}
        {project.referenceNumber && (
          <div className="flex items-start gap-2 text-sm">
            <span className="w-20 shrink-0 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
              Reference
            </span>
            <span className="min-w-0 text-slate-700 dark:text-slate-300">
              <code className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                {project.referenceNumber}
              </code>
            </span>
          </div>
        )}

        {/* Work Type */}
        {project.workType && (
          <div className="flex items-start gap-2 text-sm">
            <span className="w-20 shrink-0 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
              Type
            </span>
            <span className="min-w-0 text-slate-700 dark:text-slate-300">{project.workType}</span>
          </div>
        )}

        {/* Priority */}
        {project.priority && (
          <div className="flex items-start gap-2 text-sm">
            <span className="w-20 shrink-0 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
              Priority
            </span>
            <span className="min-w-0">
              <Badge
                variant={
                  project.priority === "HIGH"
                    ? "warning"
                    : project.priority === "MEDIUM"
                      ? "neutral"
                      : "success"
                }
              >
                {project.priority.charAt(0) + project.priority.slice(1).toLowerCase()}
              </Badge>
            </span>
          </div>
        )}

        {/* Due Date */}
        {project.dueDate && (
          <div className="flex items-start gap-2 text-sm">
            <span className="w-20 shrink-0 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
              Due
            </span>
            <span
              className={cn(
                "inline-flex min-w-0 items-center gap-1",
                project.status === "ACTIVE" && isOverdue(project.dueDate)
                  ? "font-medium text-red-600 dark:text-red-400"
                  : "text-slate-700 dark:text-slate-300"
              )}
            >
              {project.status === "ACTIVE" && isOverdue(project.dueDate) ? (
                <AlertTriangle className="size-3.5 text-red-600" />
              ) : (
                <Calendar className="size-3.5" />
              )}
              {formatLocalDate(project.dueDate)}
            </span>
          </div>
        )}

        {/* Created Date */}
        <div className="flex items-start gap-2 text-sm">
          <span className="w-20 shrink-0 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Created
          </span>
          <span className="min-w-0 text-slate-700 dark:text-slate-300">
            {formatDate(project.createdAt)}
          </span>
        </div>
      </div>

      {/* (C) Custom Fields */}
      <div className="space-y-3 border-t border-slate-200 p-4 dark:border-slate-800">
        <FieldGroupSelector
          entityType="PROJECT"
          entityId={project.id}
          appliedFieldGroups={project.appliedFieldGroups ?? []}
          slug={slug}
          canManage={canManage}
          allGroups={fieldGroups}
        />
        <CustomFieldSection
          entityType="PROJECT"
          entityId={project.id}
          customFields={project.customFields ?? {}}
          appliedFieldGroups={project.appliedFieldGroups ?? []}
          editable={canEdit}
          slug={slug}
          fieldDefinitions={fieldDefinitions}
          fieldGroups={fieldGroups}
          groupMembers={groupMembers}
        />
      </div>

      {/* (D) Tags */}
      <div className="space-y-2 border-t border-slate-200 p-4 dark:border-slate-800">
        <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
          Tags
        </p>
        <TagInput
          entityType="PROJECT"
          entityId={project.id}
          tags={projectTags}
          allTags={allTags}
          editable={canEdit}
          canInlineCreate={isAdmin}
          slug={slug}
        />
      </div>

      {/* Spacer to push footer to bottom */}
      <div className="flex-1" />

      {/* (E) Sticky Footer — Primary Lifecycle Action */}
      <div
        className="sticky bottom-0 border-t border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"
        data-testid="sidebar-footer"
      >
        <div data-testid="sidebar-lifecycle-action" className="flex w-full flex-col gap-2">
          {/* ACTIVE with matter_closure module → MatterClosureAction (self-gates internally) */}
          {project.status === "ACTIVE" && (
            <MatterClosureAction
              slug={slug}
              projectId={project.id}
              projectName={project.name}
              projectStatus={project.status}
            />
          )}
          {/* CLOSED → MatterReopenAction (self-gates on module + capability) */}
          {project.status === "CLOSED" && (
            <MatterReopenAction
              slug={slug}
              projectId={project.id}
              projectName={project.name}
              projectStatus={project.status}
            />
          )}
          {/* ACTIVE (Complete when no module), COMPLETED (Archive), ARCHIVED (Restore) */}
          {(project.status === "ACTIVE" ||
            project.status === "COMPLETED" ||
            project.status === "ARCHIVED") &&
            isAdmin && (
              <ProjectLifecycleActions
                slug={slug}
                projectId={project.id}
                projectName={project.name}
                projectStatus={project.status}
              />
            )}
        </div>
      </div>
    </div>
  );
}
