"use client";

import { useMemo, useState } from "react";
import { ChevronDown, ChevronRight, Inbox } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { EmptyState } from "@/components/empty-state";
import type { MyWorkTaskItem } from "@/lib/types";
import { MyWorkTaskTable } from "./task-table";

/** K-97: Available Tasks rendered every row unpaged (82 for an owner); ten per page. */
export const AVAILABLE_TASKS_PAGE_SIZE = 10;

interface AvailableTaskListProps {
  tasks: MyWorkTaskItem[];
  slug: string;
  onTaskClick?: (taskId: string) => void;
}

export function AvailableTaskList({ tasks, slug, onTaskClick }: AvailableTaskListProps) {
  const [isOpen, setIsOpen] = useState(tasks.length > 0);
  // The page lives here, so collapsing and expanding the section keeps it.
  const [pageIndex, setPageIndex] = useState(0);
  const pageCount = Math.ceil(tasks.length / AVAILABLE_TASKS_PAGE_SIZE);
  // A refresh can shrink the list (a task gets assigned): never point past the last page.
  const safeIndex = Math.min(pageIndex, Math.max(pageCount - 1, 0));
  const pageRows = useMemo(
    () =>
      tasks.slice(
        safeIndex * AVAILABLE_TASKS_PAGE_SIZE,
        (safeIndex + 1) * AVAILABLE_TASKS_PAGE_SIZE
      ),
    [tasks, safeIndex]
  );

  return (
    <div className="space-y-4">
      <button type="button" onClick={() => setIsOpen(!isOpen)} className="flex items-center gap-2">
        {isOpen ? (
          <ChevronDown className="size-4 text-slate-500" />
        ) : (
          <ChevronRight className="size-4 text-slate-500" />
        )}
        <h2 className="text-foreground font-semibold">Available Tasks</h2>
        <Badge variant="neutral">{tasks.length}</Badge>
      </button>

      {isOpen && (
        <>
          {tasks.length === 0 ? (
            <EmptyState
              variant="compact"
              icon={Inbox}
              title="No available tasks"
              description="Unassigned tasks in your matters will appear here"
            />
          ) : (
            <MyWorkTaskTable
              tasks={pageRows}
              slug={slug}
              columns="available"
              overdueTone="per-task"
              onTaskClick={onTaskClick}
              pagination={{ pageIndex: safeIndex, pageCount, onPageChange: setPageIndex }}
            />
          )}
        </>
      )}
    </div>
  );
}
