"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { TagDialog } from "@/components/tags/TagDialog";
import { DeleteTagDialog } from "@/components/tags/DeleteTagDialog";
import { Pencil, Plus, Trash2, Tag } from "lucide-react";
import type { TagResponse } from "@/lib/types";

function getContrastColor(hexColor: string): string {
  const r = parseInt(hexColor.slice(1, 3), 16);
  const g = parseInt(hexColor.slice(3, 5), 16);
  const b = parseInt(hexColor.slice(5, 7), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? "#000000" : "#FFFFFF";
}

interface TagsContentProps {
  slug: string;
  tags: TagResponse[];
  canManage: boolean;
}

export function TagsContent({ slug, tags, canManage }: TagsContentProps) {
  return (
    <div className="space-y-6">
      {/* Actions */}
      {canManage && (
        <div className="flex justify-end">
          <TagDialog slug={slug}>
            <Button size="sm">
              <Plus className="mr-1.5 size-4" />
              Add Tag
            </Button>
          </TagDialog>
        </div>
      )}

      {/* Tags Table or Empty State */}
      {tags.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <Tag className="size-12 text-slate-300 dark:text-slate-700" />
          <h2 className="mt-4 font-display text-lg text-slate-900 dark:text-slate-100">
            No tags yet
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {canManage
              ? "Create your first tag to organize projects, tasks, and customers."
              : "No tags have been created yet."}
          </p>
          {canManage && (
            <div className="mt-4">
              <TagDialog slug={slug}>
                <Button size="sm">
                  <Plus className="mr-1.5 size-4" />
                  Add Tag
                </Button>
              </TagDialog>
            </div>
          )}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Name
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400 sm:table-cell">
                  Slug
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400 md:table-cell">
                  Color
                </th>
                {canManage && (
                  <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Actions
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {tags.map((tag) => (
                <tr
                  key={tag.id}
                  className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <Badge
                        variant="outline"
                        style={
                          tag.color
                            ? {
                                backgroundColor: tag.color,
                                color: getContrastColor(tag.color),
                                borderColor: tag.color,
                              }
                            : undefined
                        }
                      >
                        {tag.name}
                      </Badge>
                    </div>
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-600 dark:text-slate-400 sm:table-cell">
                    <code className="rounded bg-slate-100 px-1.5 py-0.5 text-xs dark:bg-slate-800">
                      {tag.slug}
                    </code>
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-600 dark:text-slate-400 md:table-cell">
                    {tag.color ? (
                      <div className="flex items-center gap-2">
                        <span
                          className="inline-block size-4 rounded border border-slate-200 dark:border-slate-700"
                          style={{ backgroundColor: tag.color }}
                        />
                        <code className="text-xs">{tag.color}</code>
                      </div>
                    ) : (
                      <span className="text-slate-400 dark:text-slate-600">
                        &mdash;
                      </span>
                    )}
                  </td>
                  {canManage && (
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <TagDialog slug={slug} tag={tag}>
                          <Button variant="ghost" size="sm">
                            <Pencil className="size-4" />
                            <span className="sr-only">Edit {tag.name}</span>
                          </Button>
                        </TagDialog>
                        <DeleteTagDialog
                          slug={slug}
                          tagId={tag.id}
                          tagName={tag.name}
                        >
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
                          >
                            <Trash2 className="size-4" />
                            <span className="sr-only">Delete {tag.name}</span>
                          </Button>
                        </DeleteTagDialog>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
