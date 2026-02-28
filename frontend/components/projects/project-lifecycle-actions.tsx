"use client";

import { useState, useTransition } from "react";
import { Check, Archive, RotateCcw, MoreHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { CompleteProjectDialog } from "@/components/projects/complete-project-dialog";
import {
  archiveProject,
  reopenProject,
} from "@/app/(app)/org/[slug]/projects/actions";
import type { ProjectStatus } from "@/lib/types";

interface ProjectLifecycleActionsProps {
  slug: string;
  projectId: string;
  projectName: string;
  projectStatus: ProjectStatus;
}

export function ProjectLifecycleActions({
  slug,
  projectId,
  projectName,
  projectStatus,
}: ProjectLifecycleActionsProps) {
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  function handleArchive() {
    setError(null);
    startTransition(async () => {
      const result = await archiveProject(slug, projectId);
      if (!result.success) {
        setError(result.error ?? "Failed to archive project.");
      }
    });
  }

  function handleReopen() {
    setError(null);
    startTransition(async () => {
      const result = await reopenProject(slug, projectId);
      if (!result.success) {
        setError(result.error ?? "Failed to reopen project.");
      }
    });
  }

  return (
    <>
      {/* ACTIVE: Complete button + Archive in overflow */}
      {projectStatus === "ACTIVE" && (
        <>
          <CompleteProjectDialog
            slug={slug}
            projectId={projectId}
            projectName={projectName}
          >
            <Button
              size="sm"
              variant="soft"
              data-testid="complete-project-btn"
            >
              <Check className="mr-1.5 size-4" />
              Complete Project
            </Button>
          </CompleteProjectDialog>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="size-8"
                data-testid="project-overflow-menu"
              >
                <MoreHorizontal className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={handleArchive} disabled={isPending}>
                <Archive className="mr-2 size-4" />
                Archive Project
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </>
      )}

      {/* COMPLETED: Archive button + Reopen in overflow */}
      {projectStatus === "COMPLETED" && (
        <>
          <Button
            size="sm"
            variant="outline"
            onClick={handleArchive}
            disabled={isPending}
            data-testid="archive-project-btn"
          >
            <Archive className="mr-1.5 size-4" />
            Archive
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="size-8"
                data-testid="project-overflow-menu"
              >
                <MoreHorizontal className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={handleReopen} disabled={isPending}>
                <RotateCcw className="mr-2 size-4" />
                Reopen Project
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </>
      )}

      {/* ARCHIVED: Restore button */}
      {projectStatus === "ARCHIVED" && (
        <Button
          size="sm"
          variant="outline"
          onClick={handleReopen}
          disabled={isPending}
          data-testid="restore-project-btn"
        >
          <RotateCcw className="mr-1.5 size-4" />
          Restore
        </Button>
      )}

      {error && (
        <p className="text-xs text-red-600 dark:text-red-400" role="alert">
          {error}
        </p>
      )}
    </>
  );
}
