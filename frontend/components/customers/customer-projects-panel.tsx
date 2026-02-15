"use client";

import { useState, useTransition } from "react";
import { FolderKanban, Plus, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { Button } from "@/components/ui/button";
import { LinkProjectDialog } from "@/components/customers/link-project-dialog";
import { unlinkProject } from "@/app/(app)/org/[slug]/customers/[id]/actions";
import { formatDate } from "@/lib/format";
import type { Project } from "@/lib/types";
import Link from "next/link";

interface CustomerProjectsPanelProps {
  projects: Project[];
  slug: string;
  customerId: string;
  canManage: boolean;
}

export function CustomerProjectsPanel({
  projects,
  slug,
  customerId,
  canManage,
}: CustomerProjectsPanelProps) {
  const [isPending, startTransition] = useTransition();
  const [unlinkingProjectId, setUnlinkingProjectId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleUnlink(projectId: string) {
    setError(null);
    setUnlinkingProjectId(projectId);

    startTransition(async () => {
      try {
        const result = await unlinkProject(slug, customerId, projectId);
        if (!result.success) {
          setError(result.error ?? "Failed to unlink project.");
        }
      } catch {
        setError("An unexpected error occurred.");
      } finally {
        setUnlinkingProjectId(null);
      }
    });
  }

  const header = (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-slate-900 dark:text-slate-100">Projects</h2>
        {projects.length > 0 && <Badge variant="neutral">{projects.length}</Badge>}
      </div>
      {canManage && (
        <LinkProjectDialog
          slug={slug}
          customerId={customerId}
          existingProjects={projects}
        >
          <Button size="sm" variant="outline">
            <Plus className="mr-1.5 size-4" />
            Link Project
          </Button>
        </LinkProjectDialog>
      )}
    </div>
  );

  if (projects.length === 0) {
    return (
      <div className="space-y-4">
        {header}
        <EmptyState
          icon={FolderKanban}
          title="No linked projects"
          description="Link projects to this customer to track their work"
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {header}
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Project
              </th>
              <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
                Description
              </th>
              <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 lg:table-cell dark:text-slate-400">
                Created
              </th>
              {canManage && (
                <th className="w-[60px] px-4 py-3" />
              )}
            </tr>
          </thead>
          <tbody>
            {projects.map((project) => {
              const isUnlinking = unlinkingProjectId === project.id;

              return (
                <tr
                  key={project.id}
                  className="group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/org/${slug}/projects/${project.id}`}
                      className="font-medium text-slate-950 hover:underline dark:text-slate-50"
                    >
                      {project.name}
                    </Link>
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-600 sm:table-cell dark:text-slate-400">
                    <span className="line-clamp-1">
                      {project.description || "\u2014"}
                    </span>
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-400 lg:table-cell dark:text-slate-600">
                    {formatDate(project.createdAt)}
                  </td>
                  {canManage && (
                    <td className="px-4 py-3">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="size-8 p-0 text-slate-400 hover:text-red-600 dark:text-slate-600 dark:hover:text-red-400"
                        onClick={() => handleUnlink(project.id)}
                        disabled={isUnlinking || isPending}
                        title="Unlink project"
                      >
                        <X className="size-4" />
                        <span className="sr-only">Unlink</span>
                      </Button>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
