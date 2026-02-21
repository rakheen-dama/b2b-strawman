"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Plus, Trash2, ArrowUp, ArrowDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  createProjectTemplateAction,
  updateProjectTemplateAction,
} from "@/app/(app)/org/[slug]/settings/project-templates/actions";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { TagResponse } from "@/lib/types";

interface TemplateEditorProps {
  slug: string;
  template?: ProjectTemplateResponse;
  availableTags: TagResponse[];
}

interface TaskRow {
  key: string;
  name: string;
  description: string;
  estimatedHours: string;
  billable: boolean;
  assigneeRole: "PROJECT_LEAD" | "ANY_MEMBER" | "UNASSIGNED";
}

export function TemplateEditor({ slug, template, availableTags }: TemplateEditorProps) {
  const router = useRouter();
  const nextKeyRef = useRef(0);

  function newTask(): TaskRow {
    return {
      key: `task-${nextKeyRef.current++}`,
      name: "",
      description: "",
      estimatedHours: "",
      billable: false,
      assigneeRole: "UNASSIGNED",
    };
  }

  const [name, setName] = useState(template?.name ?? "");
  const [namePattern, setNamePattern] = useState(template?.namePattern ?? "");
  const [description, setDescription] = useState(template?.description ?? "");
  const [billableDefault, setBillableDefault] = useState(template?.billableDefault ?? false);
  const [tasks, setTasks] = useState<TaskRow[]>(() => {
    if (template?.tasks && template.tasks.length > 0) {
      return template.tasks
        .slice()
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((t) => ({
          key: `task-${nextKeyRef.current++}`,
          name: t.name,
          description: t.description ?? "",
          estimatedHours: t.estimatedHours != null ? String(t.estimatedHours) : "",
          billable: t.billable,
          assigneeRole: t.assigneeRole,
        }));
    }
    return [];
  });
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>(() =>
    template?.tags?.map((t) => t.id) ?? [],
  );
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function addTask() {
    setTasks((prev) => [...prev, newTask()]);
  }

  function removeTask(key: string) {
    setTasks((prev) => prev.filter((t) => t.key !== key));
  }

  function updateTask(key: string, updates: Partial<TaskRow>) {
    setTasks((prev) => prev.map((t) => (t.key === key ? { ...t, ...updates } : t)));
  }

  function moveTaskUp(index: number) {
    setTasks((prev) => {
      if (index <= 0) return prev;
      const next = [...prev];
      [next[index - 1], next[index]] = [next[index], next[index - 1]];
      return next;
    });
  }

  function moveTaskDown(index: number) {
    setTasks((prev) => {
      if (index >= prev.length - 1) return prev;
      const next = [...prev];
      [next[index], next[index + 1]] = [next[index + 1], next[index]];
      return next;
    });
  }

  async function handleSave() {
    if (!name.trim() || !namePattern.trim()) {
      setError("Name and Name Pattern are required.");
      return;
    }

    const emptyTasks = tasks.filter((t) => !t.name.trim());
    if (emptyTasks.length > 0) {
      setError("All tasks must have a name.");
      return;
    }

    setIsSaving(true);
    setError(null);

    const payload = {
      name: name.trim(),
      namePattern: namePattern.trim(),
      description: description.trim() || undefined,
      billableDefault,
      tasks: tasks.map((t, index) => ({
        name: t.name.trim(),
        description: t.description.trim() || undefined,
        estimatedHours: t.estimatedHours ? Number(t.estimatedHours) : undefined,
        sortOrder: index,
        billable: t.billable,
        assigneeRole: t.assigneeRole,
      })),
      tagIds: selectedTagIds,
    };

    try {
      if (template) {
        const result = await updateProjectTemplateAction(slug, template.id, payload);
        if (result.success) {
          // Stay on page after save
        } else {
          setError(result.error ?? "Failed to update template.");
        }
      } else {
        const result = await createProjectTemplateAction(slug, payload);
        if (result.success) {
          router.push(`/org/${slug}/settings/project-templates`);
        } else {
          setError(result.error ?? "Failed to create template.");
        }
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-8">
      {/* Section 1 — Template Metadata */}
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="template-name">Name</Label>
          <Input
            id="template-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Monthly Accounting Package"
            required
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="template-name-pattern">Name Pattern</Label>
          <Input
            id="template-name-pattern"
            value={namePattern}
            onChange={(e) => setNamePattern(e.target.value)}
            placeholder="e.g. {customer} — {month} {year}"
            required
          />
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Tokens:{" "}
            <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
              {"{customer}"}
            </code>
            ,{" "}
            <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
              {"{month}"}
            </code>
            ,{" "}
            <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
              {"{year}"}
            </code>
            ,{" "}
            <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
              {"{month_short}"}
            </code>
            ,{" "}
            <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
              {"{period_start}"}
            </code>
            ,{" "}
            <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
              {"{period_end}"}
            </code>
          </p>
        </div>

        <div className="space-y-2">
          <Label htmlFor="template-description">Description</Label>
          <Textarea
            id="template-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional description"
            rows={2}
          />
        </div>

        <div className="flex items-center gap-2">
          <input
            id="template-billable-default"
            type="checkbox"
            checked={billableDefault}
            onChange={(e) => setBillableDefault(e.target.checked)}
            className="size-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
          />
          <Label htmlFor="template-billable-default" className="cursor-pointer font-normal">
            Tasks billable by default
          </Label>
        </div>
      </div>

      {/* Section 2 — Task List */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Tasks</h2>
          <Button variant="outline" size="sm" onClick={addTask} type="button">
            <Plus className="mr-1.5 size-4" />
            Add Task
          </Button>
        </div>

        {tasks.length === 0 ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No tasks yet. Add a task to define the project structure.
          </p>
        ) : (
          <div className="space-y-3">
            {tasks.map((task, index) => (
              <div
                key={task.key}
                className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"
              >
                <div className="space-y-3">
                  <div className="flex items-center gap-2">
                    <div className="flex flex-col gap-0.5">
                      <Button
                        variant="ghost"
                        size="icon"
                        className="size-6"
                        onClick={() => moveTaskUp(index)}
                        disabled={index === 0}
                        type="button"
                        title="Move up"
                      >
                        <ArrowUp className="size-3" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="size-6"
                        onClick={() => moveTaskDown(index)}
                        disabled={index === tasks.length - 1}
                        type="button"
                        title="Move down"
                      >
                        <ArrowDown className="size-3" />
                      </Button>
                    </div>
                    <Input
                      value={task.name}
                      onChange={(e) => updateTask(task.key, { name: e.target.value })}
                      placeholder="Task name"
                      className="flex-1"
                    />
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-8 shrink-0 text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950"
                      onClick={() => removeTask(task.key)}
                      type="button"
                      title="Remove task"
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                    <Input
                      value={task.description}
                      onChange={(e) => updateTask(task.key, { description: e.target.value })}
                      placeholder="Description (optional)"
                    />
                    <Input
                      type="number"
                      value={task.estimatedHours}
                      onChange={(e) => updateTask(task.key, { estimatedHours: e.target.value })}
                      placeholder="Est. hours"
                      min={0}
                      step={0.5}
                    />
                    <select
                      value={task.assigneeRole}
                      onChange={(e) =>
                        updateTask(task.key, {
                          assigneeRole: e.target.value as TaskRow["assigneeRole"],
                        })
                      }
                      className="flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
                    >
                      <option value="UNASSIGNED">Unassigned</option>
                      <option value="PROJECT_LEAD">Project Lead</option>
                      <option value="ANY_MEMBER">Any Member</option>
                    </select>
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      id={`task-billable-${task.key}`}
                      checked={task.billable}
                      onChange={(e) => updateTask(task.key, { billable: e.target.checked })}
                      className="size-3.5 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                    />
                    <label
                      htmlFor={`task-billable-${task.key}`}
                      className="cursor-pointer text-sm text-slate-600 dark:text-slate-400"
                    >
                      Billable
                    </label>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Section 3 — Tags */}
      <div className="space-y-4">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Tags</h2>
        {availableTags.length === 0 ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">No tags available.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {availableTags.map((tag) => (
              <label
                key={tag.id}
                className="flex cursor-pointer items-center gap-1.5 text-sm"
              >
                <input
                  type="checkbox"
                  checked={selectedTagIds.includes(tag.id)}
                  onChange={(e) => {
                    if (e.target.checked) {
                      setSelectedTagIds((prev) => [...prev, tag.id]);
                    } else {
                      setSelectedTagIds((prev) => prev.filter((id) => id !== tag.id));
                    }
                  }}
                  className="size-3.5 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                />
                {tag.color ? (
                  <span
                    className="inline-block size-2.5 rounded-full"
                    style={{ backgroundColor: tag.color }}
                  />
                ) : null}
                {tag.name}
              </label>
            ))}
          </div>
        )}
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      <div className="flex justify-end gap-3">
        <Button
          variant="soft"
          type="button"
          onClick={() => router.push(`/org/${slug}/settings/project-templates`)}
        >
          Cancel
        </Button>
        <Button
          type="button"
          onClick={handleSave}
          disabled={isSaving || !name.trim() || !namePattern.trim()}
        >
          {isSaving ? "Saving..." : template ? "Save Changes" : "Create Template"}
        </Button>
      </div>
    </div>
  );
}
