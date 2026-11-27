"use client";

import { CheckCircle } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { EmptyState } from "@/components/empty-state";
import type { MyWorkTaskItem } from "@/lib/types";
import { MyWorkTaskTable } from "./task-table";

// Not rendered in production today (only its test imports it; found.md, 668A) — kept on the same
// task table as the urgency groups so it does not drift.

interface AssignedTaskListProps {
  tasks: MyWorkTaskItem[];
  slug: string;
}

export function AssignedTaskList({ tasks, slug }: AssignedTaskListProps) {
  if (tasks.length === 0) {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <h2 className="text-foreground font-semibold">My Tasks</h2>
        </div>
        <EmptyState
          variant="compact"
          icon={CheckCircle}
          title="No tasks assigned"
          description="Tasks assigned to you will appear here"
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <h2 className="text-foreground font-semibold">My Tasks</h2>
        <Badge variant="neutral">{tasks.length}</Badge>
      </div>

      <MyWorkTaskTable tasks={tasks} slug={slug} columns="full" overdueTone="per-task" />
    </div>
  );
}
