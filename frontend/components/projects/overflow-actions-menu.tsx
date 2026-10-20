"use client";

import { useState, useTransition } from "react";
import { Archive, LayoutTemplate, MoreHorizontal, Pencil, Trash2 } from "lucide-react";
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
import { SaveAsTemplateDialog } from "@/components/templates/SaveAsTemplateDialog";
import { EditProjectDialog } from "@/components/projects/edit-project-dialog";
import { DeleteProjectDialog } from "@/components/projects/delete-project-dialog";
import { archiveProject } from "@/app/(app)/org/[slug]/matters/actions";
import type { Matter, Task, TagResponse, TemplateListResponse, MatterStatus } from "@/lib/types";

interface OverflowActionsMenuProps {
  slug: string;
  projectId: string;
  projectName: string;
  projectStatus: MatterStatus;
  canEdit: boolean;
  canManage: boolean;
  isAdmin: boolean;
  isOwner: boolean;
  /** Document templates for Generate Document sub-menu */
  templates: TemplateListResponse[];
  /** Primary customer (for Generate Document) */
  primaryCustomer: { id: string; name: string; email: string } | null;
  projectTags: TagResponse[];
  /** Full project object for EditProjectDialog */
  project: Matter;
  /** Tasks for SaveAsTemplateDialog */
  tasks: Task[];
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
}: OverflowActionsMenuProps) {
  const [isPending, startTransition] = useTransition();

  // Controlled dialog states — dialogs rendered outside DropdownMenuContent to avoid Slot collision
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [templateOpen, setTemplateOpen] = useState(false);

  const [archiveError, setArchiveError] = useState<string | null>(null);

  const isArchived = projectStatus === "ARCHIVED";

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
          {/* Save as Template */}
          {canManage && (
            <DropdownMenuItem onSelect={() => setTemplateOpen(true)}>
              <LayoutTemplate className="mr-2 size-4" />
              Save as Template
            </DropdownMenuItem>
          )}

          {canManage && (canEdit || isAdmin || isOwner) && <DropdownMenuSeparator />}

          {/* Edit Matter */}
          {canEdit && (
            <DropdownMenuItem onSelect={() => setEditOpen(true)}>
              <Pencil className="mr-2 size-4" />
              Edit Matter
            </DropdownMenuItem>
          )}

          {/* Archive Matter */}
          {isAdmin && (
            <DropdownMenuItem onSelect={handleArchive} disabled={isPending}>
              <Archive className="mr-2 size-4" />
              Archive Matter
            </DropdownMenuItem>
          )}

          {/* Delete Matter */}
          {isOwner && (
            <DropdownMenuItem
              onSelect={() => setDeleteOpen(true)}
              className="text-red-600 focus:text-red-600 dark:text-red-400 dark:focus:text-red-400"
            >
              <Trash2 className="mr-2 size-4" />
              Delete Matter
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {/* Dialogs rendered outside DropdownMenuContent — no Slot collision (OBS-2103) */}
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
