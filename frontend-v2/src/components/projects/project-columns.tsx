"use client";

import type { ColumnDef } from "@tanstack/react-table";
import type { Project, LightweightBudgetStatus } from "@/lib/types";
import { StatusBadge } from "@/components/ui/status-badge";
import { formatLocalDate, isOverdue } from "@/lib/format";
import { AlertTriangle, Calendar } from "lucide-react";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";

export interface ProjectRow extends Project {
  customerName?: string | null;
  tasksDone?: number;
  tasksTotal?: number;
  budgetStatus?: LightweightBudgetStatus | null;
}

export function getProjectColumns(): ColumnDef<ProjectRow, unknown>[] {
  return [
    {
      accessorKey: "name",
      header: "Name",
      cell: ({ row }) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-slate-900">
            {row.original.name}
          </p>
          {row.original.description && (
            <p className="truncate text-xs text-slate-500">
              {row.original.description}
            </p>
          )}
        </div>
      ),
      size: 280,
    },
    {
      accessorKey: "customerName",
      header: "Customer",
      cell: ({ row }) => (
        <span className="text-sm text-slate-600">
          {row.original.customerName ?? "Internal"}
        </span>
      ),
      size: 160,
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => <StatusBadge status={row.original.status} />,
      size: 110,
    },
    {
      id: "tasks",
      header: "Tasks",
      cell: ({ row }) => {
        const done = row.original.tasksDone ?? 0;
        const total = row.original.tasksTotal ?? 0;
        return (
          <span className="font-mono text-sm tabular-nums text-slate-600">
            {done}/{total}
          </span>
        );
      },
      size: 80,
      enableSorting: false,
    },
    {
      id: "budget",
      header: "Budget",
      cell: ({ row }) => {
        const budget = row.original.budgetStatus;
        if (!budget || budget.overallStatus === null) {
          return <span className="text-sm text-slate-400">--</span>;
        }
        const pct = Math.round(budget.hoursConsumedPct);
        return (
          <div className="flex items-center gap-2">
            <Progress
              value={Math.min(pct, 100)}
              className="h-1.5 w-16"
            />
            <span
              className={cn(
                "font-mono text-xs tabular-nums",
                budget.overallStatus === "ON_TRACK" && "text-emerald-600",
                budget.overallStatus === "AT_RISK" && "text-amber-600",
                budget.overallStatus === "OVER_BUDGET" && "text-red-600"
              )}
            >
              {pct}%
            </span>
          </div>
        );
      },
      size: 120,
      enableSorting: false,
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
          row.original.status === "ACTIVE" && isOverdue(dueDate);
        return (
          <span
            className={cn(
              "inline-flex items-center gap-1 text-sm",
              overdue
                ? "font-medium text-red-600"
                : "text-slate-600"
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
}
