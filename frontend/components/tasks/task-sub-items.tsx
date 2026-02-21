"use client";

import { useEffect, useState, useTransition, useRef } from "react";
import { X } from "lucide-react";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  fetchTaskItems,
  addTaskItem,
  toggleTaskItem,
  deleteTaskItem,
} from "@/app/(app)/org/[slug]/projects/[id]/task-item-actions";
import type { TaskItem } from "@/lib/types";

interface TaskSubItemsProps {
  taskId: string;
  slug: string;
  projectId: string;
  canManage: boolean;
}

export function TaskSubItems({
  taskId,
  slug,
  projectId,
  canManage,
}: TaskSubItemsProps) {
  const [items, setItems] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [newTitle, setNewTitle] = useState("");
  const [, startTransition] = useTransition();
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    fetchTaskItems(taskId)
      .then((data) => {
        if (!cancelled) {
          setItems(data.sort((a, b) => a.sortOrder - b.sortOrder));
          setLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setItems([]);
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [taskId]);

  const completedCount = items.filter((i) => i.completed).length;
  const totalCount = items.length;
  const progressPct = totalCount > 0 ? (completedCount / totalCount) * 100 : 0;

  function handleToggle(item: TaskItem) {
    // Optimistic update
    setItems((prev) =>
      prev.map((i) =>
        i.id === item.id ? { ...i, completed: !i.completed } : i
      )
    );

    startTransition(async () => {
      const result = await toggleTaskItem(slug, projectId, taskId, item.id);
      if (!result.success) {
        // Revert
        setItems((prev) =>
          prev.map((i) =>
            i.id === item.id ? { ...i, completed: item.completed } : i
          )
        );
      }
    });
  }

  function handleAdd() {
    const title = newTitle.trim();
    if (!title) return;

    const sortOrder = items.length;
    const optimisticItem: TaskItem = {
      id: `temp-${Date.now()}`,
      taskId,
      title,
      completed: false,
      sortOrder,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    // Optimistic add
    setItems((prev) => [...prev, optimisticItem]);
    setNewTitle("");

    startTransition(async () => {
      const result = await addTaskItem(slug, projectId, taskId, title, sortOrder);
      if (!result.success) {
        // Revert
        setItems((prev) => prev.filter((i) => i.id !== optimisticItem.id));
      } else {
        // Refetch to get real ID
        const freshItems = await fetchTaskItems(taskId);
        setItems(freshItems.sort((a, b) => a.sortOrder - b.sortOrder));
      }
    });
  }

  function handleDelete(item: TaskItem) {
    // Optimistic delete
    setItems((prev) => prev.filter((i) => i.id !== item.id));

    startTransition(async () => {
      const result = await deleteTaskItem(slug, projectId, taskId, item.id);
      if (!result.success) {
        // Revert
        setItems((prev) =>
          [...prev, item].sort((a, b) => a.sortOrder - b.sortOrder)
        );
      }
    });
  }

  return (
    <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
      {/* Section header with progress */}
      <div className="mb-3 flex items-center gap-2">
        <h3 className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
          Sub-Items
        </h3>
        {totalCount > 0 && (
          <span className="text-xs text-slate-400 dark:text-slate-500">
            {completedCount}/{totalCount}
          </span>
        )}
      </div>

      {/* Progress bar */}
      {totalCount > 0 && (
        <div className="mb-3 h-1 rounded-full bg-slate-200 dark:bg-slate-700">
          <div
            className="h-1 rounded-full bg-teal-500 transition-all duration-200"
            style={{ width: `${progressPct}%` }}
          />
        </div>
      )}

      {/* Loading state */}
      {loading && (
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Loading sub-items...
        </p>
      )}

      {/* Empty state */}
      {!loading && totalCount === 0 && (
        <p className="text-sm text-slate-400 dark:text-slate-500">
          No sub-items yet
        </p>
      )}

      {/* Items list */}
      {!loading && totalCount > 0 && (
        <div className="space-y-0">
          {items.map((item) => (
            <div
              key={item.id}
              className="group flex items-center gap-2.5 py-1.5"
            >
              <Checkbox
                checked={item.completed}
                onCheckedChange={() => handleToggle(item)}
                className="size-4"
                aria-label={`Toggle ${item.title}`}
              />
              <span
                data-testid={`item-title-${item.id}`}
                className={
                  item.completed
                    ? "flex-1 text-sm line-through text-slate-400 dark:text-slate-500"
                    : "flex-1 text-sm text-slate-700 dark:text-slate-300"
                }
              >
                {item.title}
              </span>
              {canManage && (
                <button
                  type="button"
                  onClick={() => handleDelete(item)}
                  className="opacity-0 transition-opacity group-hover:opacity-100 text-slate-400 hover:text-red-500 dark:text-slate-500 dark:hover:text-red-400"
                  aria-label={`Delete ${item.title}`}
                >
                  <X className="size-3.5" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Add form */}
      <div className="mt-3 flex items-center gap-2">
        <Input
          ref={inputRef}
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              handleAdd();
            }
          }}
          placeholder="Add a sub-item..."
          className="h-8 text-sm"
        />
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={handleAdd}
          disabled={!newTitle.trim()}
        >
          Add
        </Button>
      </div>
    </div>
  );
}
