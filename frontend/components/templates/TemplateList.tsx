"use client";

import { useState } from "react";
import Link from "next/link";
import { LayoutTemplate, Pencil, Copy, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  deleteTemplateAction,
  duplicateTemplateAction,
} from "@/app/(app)/org/[slug]/settings/project-templates/actions";
import type { ProjectTemplateResponse } from "@/lib/api/templates";

interface TemplateListProps {
  slug: string;
  templates: ProjectTemplateResponse[];
  canManage: boolean;
}

export function TemplateList({ slug, templates, canManage }: TemplateListProps) {
  const [errorMessages, setErrorMessages] = useState<Record<string, string>>({});
  const [deleting, setDeleting] = useState<string | null>(null);

  async function handleDuplicate(id: string) {
    const result = await duplicateTemplateAction(slug, id);
    if (!result.success && result.error) {
      setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
    }
  }

  async function handleDelete(id: string) {
    if (deleting) return;
    setDeleting(id);
    try {
      const result = await deleteTemplateAction(slug, id);
      if (!result.success && result.error) {
        setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
      }
    } finally {
      setDeleting(null);
    }
  }

  if (templates.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <LayoutTemplate className="size-12 text-slate-300 dark:text-slate-700" />
        <h2 className="mt-4 font-display text-lg text-slate-900 dark:text-slate-100">
          No project templates yet.
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Create your first template to standardize how projects are structured.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Name
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Source
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Tasks
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Tags
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Status
            </th>
            {canManage && (
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Actions
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {templates.map((template) => (
            <tr
              key={template.id}
              className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
            >
              <td className="px-4 py-3">
                <p className="font-medium text-slate-900 dark:text-slate-100">{template.name}</p>
                {template.description && (
                  <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                    {template.description}
                  </p>
                )}
                {errorMessages[template.id] && (
                  <p className="mt-1 text-xs text-red-600 dark:text-red-400">
                    {errorMessages[template.id]}
                  </p>
                )}
              </td>
              <td className="px-4 py-3">
                {template.source === "FROM_PROJECT" ? (
                  <Badge variant="lead">From Project</Badge>
                ) : (
                  <Badge variant="neutral">Manual</Badge>
                )}
              </td>
              <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                {template.taskCount}
              </td>
              <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                {template.tagCount}
              </td>
              <td className="px-4 py-3">
                {template.active ? (
                  <Badge variant="success">Active</Badge>
                ) : (
                  <Badge variant="neutral">Inactive</Badge>
                )}
              </td>
              {canManage && (
                <td className="px-4 py-3 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <Link href={`/org/${slug}/settings/project-templates/${template.id}`}>
                      <Button variant="ghost" size="sm" title="Edit template">
                        <Pencil className="size-4" />
                        <span className="sr-only">Edit {template.name}</span>
                      </Button>
                    </Link>
                    <Button
                      variant="ghost"
                      size="sm"
                      title="Duplicate template"
                      onClick={() => handleDuplicate(template.id)}
                    >
                      <Copy className="size-4" />
                      <span className="sr-only">Duplicate {template.name}</span>
                    </Button>
                    <AlertDialog>
                      <AlertDialogTrigger asChild>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
                          title="Delete template"
                        >
                          <Trash2 className="size-4" />
                          <span className="sr-only">Delete {template.name}</span>
                        </Button>
                      </AlertDialogTrigger>
                      <AlertDialogContent>
                        <AlertDialogHeader>
                          <AlertDialogTitle>Delete Template</AlertDialogTitle>
                          <AlertDialogDescription>
                            Are you sure you want to delete &quot;{template.name}&quot;? This
                            action cannot be undone. If this template has active recurring
                            schedules, deletion will be blocked.
                          </AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                          <AlertDialogCancel>Cancel</AlertDialogCancel>
                          <AlertDialogAction
                            className="bg-red-600 hover:bg-red-700"
                            onClick={() => handleDelete(template.id)}
                            disabled={deleting === template.id}
                          >
                            {deleting === template.id ? "Deleting..." : "Delete"}
                          </AlertDialogAction>
                        </AlertDialogFooter>
                      </AlertDialogContent>
                    </AlertDialog>
                  </div>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
