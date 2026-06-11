"use client";

import { useState, useTransition } from "react";
import { Archive, FileText, LayoutTemplate, MoreHorizontal, Pencil, Trash2 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { GenerateDocumentDropdown } from "@/components/templates/GenerateDocumentDropdown";
import { GenerateStatementOfAccountAction } from "@/components/projects/generate-statement-action";
import { CreateProposalDialog } from "@/components/proposals/create-proposal-dialog";
import { SaveAsTemplateDialog } from "@/components/templates/SaveAsTemplateDialog";
import { EditProjectDialog } from "@/components/projects/edit-project-dialog";
import { DeleteProjectDialog } from "@/components/projects/delete-project-dialog";
import { archiveProject } from "@/app/(app)/org/[slug]/projects/actions";
import { useTerminology } from "@/lib/terminology";
import type { Project, Task, TagResponse, TemplateListResponse, ProjectStatus } from "@/lib/types";

interface OverflowActionsMenuProps {
  slug: string;
  projectId: string;
  projectName: string;
  projectStatus: ProjectStatus;
  canEdit: boolean;
  canManage: boolean;
  isAdmin: boolean;
  isOwner: boolean;
  /** Document templates for Generate Document sub-menu */
  templates: TemplateListResponse[];
  /** Primary customer (for engagement letter, statement) */
  primaryCustomer: { id: string; name: string; email: string } | null;
  projectTags: TagResponse[];
  /** Full project object for EditProjectDialog */
  project: Project;
  /** Tasks for SaveAsTemplateDialog */
  tasks: Task[];
  /** Primary customer lifecycle status for engagement letter gate */
  primaryCustomerLifecycleStatus?: string;
}

export function OverflowActionsMenu({
  slug,
  projectId,
  projectName,
  projectStatus,
  canEdit,
  canManage,
  isAdmin,
  isOwner,
  templates,
  primaryCustomer,
  projectTags,
  project,
  tasks,
  primaryCustomerLifecycleStatus,
}: OverflowActionsMenuProps) {
  const { t } = useTerminology();
  const [isPending, startTransition] = useTransition();

  // Controlled dialog states — dialogs rendered outside DropdownMenuContent to avoid Slot collision
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [templateOpen, setTemplateOpen] = useState(false);
  const [proposalOpen, setProposalOpen] = useState(false);

  const [archiveError, setArchiveError] = useState<string | null>(null);

  const isArchived = projectStatus === "ARCHIVED";

  // Engagement letter gate: canManage + customer linked + customer not offboarded/anonymized
  const canCreateEngagementLetter =
    canManage &&
    primaryCustomer !== null &&
    primaryCustomerLifecycleStatus !== "OFFBOARDED" &&
    primaryCustomerLifecycleStatus !== "OFFBOARDING" &&
    primaryCustomerLifecycleStatus !== "ANONYMIZED";

  function handleArchive() {
    setArchiveError(null);
    startTransition(async () => {
      const result = await archiveProject(slug, projectId);
      if (!result.success) {
        setArchiveError(result.error ?? "Failed to archive.");
      }
    });
  }

  // Don't render for archived matters (no secondary actions available)
  if (isArchived) return null;

  return (
    <div className="flex items-center gap-1">
      {/* Generate Document — standalone button with its own dropdown (cannot nest in DropdownMenu) */}
      {canManage && templates.length > 0 && (
        <GenerateDocumentDropdown
          templates={templates}
          entityId={projectId}
          entityType="PROJECT"
          slug={slug}
          customerId={primaryCustomer?.id}
          isAdmin={isAdmin}
        />
      )}

      {/* Generate Statement of Account — self-gating component (disbursements module) */}
      <GenerateStatementOfAccountAction
        slug={slug}
        projectId={projectId}
        projectName={projectName}
        projectStatus={projectStatus}
      />

      {/* Overflow menu for remaining actions */}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="size-8"
            data-testid="overflow-actions-trigger"
          >
            <MoreHorizontal className="size-4" />
            <span className="sr-only">More actions</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" data-testid="overflow-actions-menu">
          {/* New Engagement Letter */}
          {canCreateEngagementLetter && (
            <DropdownMenuItem onSelect={() => setProposalOpen(true)}>
              <FileText className="mr-2 size-4" />
              New Engagement Letter
            </DropdownMenuItem>
          )}

          {/* Save as Template */}
          {canManage && (
            <DropdownMenuItem onSelect={() => setTemplateOpen(true)}>
              <LayoutTemplate className="mr-2 size-4" />
              Save as Template
            </DropdownMenuItem>
          )}

          {(canCreateEngagementLetter || canManage) && (canEdit || isAdmin || isOwner) && (
            <DropdownMenuSeparator />
          )}

          {/* Edit Matter */}
          {canEdit && (
            <DropdownMenuItem onSelect={() => setEditOpen(true)}>
              <Pencil className="mr-2 size-4" />
              Edit {t("Project")}
            </DropdownMenuItem>
          )}

          {/* Archive Matter */}
          {isAdmin && (
            <DropdownMenuItem onSelect={handleArchive} disabled={isPending}>
              <Archive className="mr-2 size-4" />
              Archive {t("Project")}
            </DropdownMenuItem>
          )}

          {/* Delete Matter */}
          {isOwner && (
            <DropdownMenuItem
              onSelect={() => setDeleteOpen(true)}
              className="text-red-600 focus:text-red-600 dark:text-red-400 dark:focus:text-red-400"
            >
              <Trash2 className="mr-2 size-4" />
              Delete {t("Project")}
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {/* Dialogs rendered outside DropdownMenuContent — no Slot collision (OBS-2103) */}
      {canCreateEngagementLetter && primaryCustomer && (
        <CreateProposalDialog
          slug={slug}
          customers={[primaryCustomer]}
          defaultCustomerId={primaryCustomer.id}
          defaultFeeModel="HOURLY"
          open={proposalOpen}
          onOpenChange={setProposalOpen}
        />
      )}

      {canManage && (
        <SaveAsTemplateDialog
          slug={slug}
          projectId={projectId}
          projectTasks={tasks}
          projectTags={projectTags}
          open={templateOpen}
          onOpenChange={setTemplateOpen}
        />
      )}

      {canEdit && (
        <EditProjectDialog
          project={project}
          slug={slug}
          open={editOpen}
          onOpenChange={setEditOpen}
        />
      )}

      {isOwner && (
        <DeleteProjectDialog
          slug={slug}
          projectId={projectId}
          projectName={projectName}
          open={deleteOpen}
          onOpenChange={setDeleteOpen}
        />
      )}

      {archiveError && (
        <p className="text-xs text-red-600 dark:text-red-400" role="alert">
          {archiveError}
        </p>
      )}
    </div>
  );
}
