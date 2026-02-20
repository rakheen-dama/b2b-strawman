"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { saveAsTemplateAction } from "@/app/(app)/org/[slug]/settings/project-templates/actions";
import { resolveNameTokens } from "@/lib/name-token-resolver";
import type { Task, TagResponse } from "@/lib/types";
import Link from "next/link";

type AssigneeRole = "UNASSIGNED" | "PROJECT_LEAD" | "ANY_MEMBER";

interface SaveAsTemplateDialogProps {
  slug: string;
  projectId: string;
  projectTasks: Task[];
  projectTags: TagResponse[];
  children: React.ReactNode;
}

export function SaveAsTemplateDialog({
  slug,
  projectId,
  projectTasks,
  projectTags,
  children,
}: SaveAsTemplateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successTemplateId, setSuccessTemplateId] = useState<string | null>(null);

  // Form state
  const [name, setName] = useState("");
  const [namePattern, setNamePattern] = useState("");
  const [description, setDescription] = useState("");
  const [selectedTaskIds, setSelectedTaskIds] = useState<Set<string>>(new Set());
  const [taskRoles, setTaskRoles] = useState<Record<string, AssigneeRole>>({});
  const [selectedTagIds, setSelectedTagIds] = useState<Set<string>>(new Set());

  function handleOpenChange(newOpen: boolean) {
    if (isSubmitting) return;
    if (newOpen) {
      // Reset form
      setName("");
      setNamePattern("");
      setDescription("");
      setSelectedTaskIds(new Set());
      setTaskRoles({});
      setSelectedTagIds(new Set());
      setError(null);
      setSuccessTemplateId(null);
    }
    setOpen(newOpen);
  }

  function toggleTask(taskId: string) {
    setSelectedTaskIds((prev) => {
      const next = new Set(prev);
      if (next.has(taskId)) {
        next.delete(taskId);
      } else {
        next.add(taskId);
      }
      return next;
    });
  }

  function setTaskRole(taskId: string, role: AssigneeRole) {
    setTaskRoles((prev) => ({ ...prev, [taskId]: role }));
  }

  function toggleTag(tagId: string) {
    setSelectedTagIds((prev) => {
      const next = new Set(prev);
      if (next.has(tagId)) {
        next.delete(tagId);
      } else {
        next.add(tagId);
      }
      return next;
    });
  }

  async function handleSubmit() {
    if (!name.trim() || !namePattern.trim()) {
      setError("Template name and name pattern are required.");
      return;
    }

    setError(null);
    setIsSubmitting(true);

    try {
      const rolesRecord: Record<string, string> = {};
      selectedTaskIds.forEach((taskId) => {
        rolesRecord[taskId] = taskRoles[taskId] ?? "UNASSIGNED";
      });

      const result = await saveAsTemplateAction(slug, projectId, {
        name: name.trim(),
        namePattern: namePattern.trim(),
        description: description.trim() || undefined,
        taskIds: Array.from(selectedTaskIds),
        tagIds: Array.from(selectedTagIds),
        taskRoles: rolesRecord,
      });

      if (result.success && result.data) {
        setSuccessTemplateId(result.data.id);
      } else {
        setError(result.error ?? "Failed to save as template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const namePreview = namePattern.trim()
    ? resolveNameTokens(namePattern.trim(), undefined, new Date())
    : null;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Save as Template</DialogTitle>
          <DialogDescription>
            Create a reusable project template from this project&apos;s tasks and tags.
          </DialogDescription>
        </DialogHeader>

        {successTemplateId ? (
          <div className="space-y-4 py-4">
            <p className="text-sm text-slate-700 dark:text-slate-300">
              Template &ldquo;{name}&rdquo; saved successfully.
            </p>
            <div className="flex gap-2">
              <Link href={`/org/${slug}/settings/project-templates/${successTemplateId}`}>
                <Button variant="outline" size="sm">
                  Open Template Editor
                </Button>
              </Link>
              <Button variant="plain" size="sm" onClick={() => setOpen(false)}>
                Close
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-5 py-4">
            {/* Template Name */}
            <div className="space-y-2">
              <Label htmlFor="tpl-name">Template name</Label>
              <Input
                id="tpl-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Monthly Bookkeeping"
                required
                maxLength={300}
              />
            </div>

            {/* Name Pattern */}
            <div className="space-y-2">
              <Label htmlFor="tpl-pattern">Name pattern</Label>
              <Input
                id="tpl-pattern"
                value={namePattern}
                onChange={(e) => setNamePattern(e.target.value)}
                placeholder="e.g. {customer} — {month} {year}"
                required
                maxLength={300}
              />
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Tokens:{" "}
                <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
                  {"{customer}"}
                </code>
                {", "}
                <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
                  {"{month}"}
                </code>
                {", "}
                <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
                  {"{year}"}
                </code>
                {", "}
                <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
                  {"{month_short}"}
                </code>
              </p>
              {namePreview && (
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  Preview:{" "}
                  <span className="font-medium text-slate-600 dark:text-slate-300">
                    {namePreview}
                  </span>
                </p>
              )}
            </div>

            {/* Description */}
            <div className="space-y-2">
              <Label htmlFor="tpl-desc">
                Description <span className="font-normal text-muted-foreground">(optional)</span>
              </Label>
              <Textarea
                id="tpl-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Optional description for this template..."
                rows={2}
                maxLength={2000}
              />
            </div>

            {/* Tasks */}
            {projectTasks.length > 0 && (
              <div className="space-y-2">
                <Label>Select tasks to include</Label>
                <div className="max-h-48 space-y-2 overflow-y-auto rounded-lg border border-slate-200 p-3 dark:border-slate-800">
                  {projectTasks.map((task) => {
                    const isChecked = selectedTaskIds.has(task.id);
                    return (
                      <div key={task.id} className="flex items-center gap-3">
                        <input
                          type="checkbox"
                          id={`task-${task.id}`}
                          checked={isChecked}
                          onChange={() => toggleTask(task.id)}
                          className="size-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                        />
                        <label
                          htmlFor={`task-${task.id}`}
                          className="flex-1 cursor-pointer text-sm text-slate-900 dark:text-slate-100"
                        >
                          {task.title}
                        </label>
                        {isChecked && (
                          <select
                            value={taskRoles[task.id] ?? "UNASSIGNED"}
                            onChange={(e) =>
                              setTaskRole(task.id, e.target.value as AssigneeRole)
                            }
                            className="flex h-7 rounded border border-slate-200 bg-transparent px-2 py-0.5 text-xs shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
                          >
                            <option value="UNASSIGNED">Unassigned</option>
                            <option value="PROJECT_LEAD">Project Lead</option>
                            <option value="ANY_MEMBER">Any Member</option>
                          </select>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Tags */}
            {projectTags.length > 0 && (
              <div className="space-y-2">
                <Label>Select tags to include</Label>
                <div className="flex flex-wrap gap-2">
                  {projectTags.map((tag) => (
                    <label
                      key={tag.id}
                      className="flex cursor-pointer items-center gap-1.5 text-sm"
                    >
                      <input
                        type="checkbox"
                        checked={selectedTagIds.has(tag.id)}
                        onChange={() => toggleTag(tag.id)}
                        className="size-3.5 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                      />
                      {tag.color && (
                        <span
                          className="inline-block size-2.5 rounded-full"
                          style={{ backgroundColor: tag.color }}
                        />
                      )}
                      {tag.name}
                    </label>
                  ))}
                </div>
              </div>
            )}

            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
        )}

        {!successTemplateId && (
          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => setOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              type="button"
              onClick={handleSubmit}
              disabled={isSubmitting || !name.trim() || !namePattern.trim()}
            >
              {isSubmitting ? "Saving..." : "Save as Template"}
            </Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  );
}
