"use client";

import * as React from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { Task } from "@/lib/types";
import { DataTable } from "@/components/ui/data-table";
import { DataTableToolbar } from "@/components/ui/data-table-toolbar";
import { StatusBadge } from "@/components/ui/status-badge";
import { formatLocalDate, isOverdue } from "@/lib/format";
import { AlertTriangle, Calendar, ListTodo } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

interface TaskListProps {
  tasks: Task[];
  onTaskClick?: (task: Task) => void;
}

const columns: ColumnDef<Task, unknown>[] = [
  {
    accessorKey: "title",
    header: "Title",
    cell: ({ row }) => (
      <p className="truncate font-medium text-slate-900">
        {row.original.title}
      </p>
    ),
    size: 280,
  },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ row }) => <StatusBadge status={row.original.status} />,
    size: 120,
  },
  {
    accessorKey: "priority",
    header: "Priority",
    cell: ({ row }) => <StatusBadge status={row.original.priority} />,
    size: 100,
  },
  {
    accessorKey: "assigneeName",
    header: "Assignee",
    cell: ({ row }) => (
      <span className="text-sm text-slate-600">
        {row.original.assigneeName ?? "Unassigned"}
      </span>
    ),
    size: 150,
  },
  {
    accessorKey: "dueDate",
    header: "Due Date",
    cell: ({ row }) => {
      const dueDate = row.original.dueDate;
      if (!dueDate) {
        return <span className="text-sm text-slate-400">--</span>;
      }
      const overdue =
        (row.original.status === "OPEN" || row.original.status === "IN_PROGRESS") &&
        isOverdue(dueDate);
      return (
        <span
          className={cn(
            "inline-flex items-center gap-1 text-sm",
            overdue ? "font-medium text-red-600" : "text-slate-600"
          )}
        >
          {overdue ? (
            <AlertTriangle className="size-3.5" />
          ) : (
            <Calendar className="size-3.5" />
          )}
          {formatLocalDate(dueDate)}
        </span>
      );
    },
    size: 140,
  },
];

export function TaskList({ tasks, onTaskClick }: TaskListProps) {
  const [search, setSearch] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState("all");
  const [priorityFilter, setPriorityFilter] = React.useState("all");

  const filtered = React.useMemo(() => {
    let result = tasks;

    if (search) {
      const q = search.toLowerCase();
      result = result.filter(
        (t) =>
          t.title.toLowerCase().includes(q) ||
          t.assigneeName?.toLowerCase().includes(q)
      );
    }

    if (statusFilter !== "all") {
      result = result.filter((t) => t.status === statusFilter);
    }

    if (priorityFilter !== "all") {
      result = result.filter((t) => t.priority === priorityFilter);
    }

    return result;
  }, [tasks, search, statusFilter, priorityFilter]);

  return (
    <div className="space-y-4">
      <DataTableToolbar
        searchPlaceholder="Search tasks..."
        searchValue={search}
        onSearchChange={setSearch}
        filters={
          <>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="h-8 w-[130px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Statuses</SelectItem>
                <SelectItem value="OPEN">Open</SelectItem>
                <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
                <SelectItem value="DONE">Done</SelectItem>
                <SelectItem value="CANCELLED">Cancelled</SelectItem>
              </SelectContent>
            </Select>
            <Select value={priorityFilter} onValueChange={setPriorityFilter}>
              <SelectTrigger className="h-8 w-[120px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Priority</SelectItem>
                <SelectItem value="HIGH">High</SelectItem>
                <SelectItem value="MEDIUM">Medium</SelectItem>
                <SelectItem value="LOW">Low</SelectItem>
              </SelectContent>
            </Select>
          </>
        }
      />
      <DataTable
        columns={columns}
        data={filtered}
        onRowClick={onTaskClick}
        emptyState={
          <div className="flex flex-col items-center py-16 text-center">
            <ListTodo className="size-12 text-slate-300" />
            <h3 className="mt-4 font-display text-lg text-slate-900">
              No tasks found
            </h3>
            <p className="mt-1 text-sm text-slate-500">
              {search || statusFilter !== "all" || priorityFilter !== "all"
                ? "Try adjusting your filters."
                : "Create your first task to get started."}
            </p>
          </div>
        }
      />
    </div>
  );
}
