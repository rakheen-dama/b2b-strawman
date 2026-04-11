"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowUpDown } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { CalculatedDeadline } from "@/lib/types";

interface DeadlineListViewProps {
  deadlines: CalculatedDeadline[];
  slug: string;
  selectedIds: Set<string>;
  onSelectionChange: (ids: Set<string>) => void;
}

type SortField = "dueDate" | "customerName" | "status";
type SortDir = "asc" | "desc";

function deadlineKey(d: CalculatedDeadline) {
  return `${d.customerId}__${d.deadlineTypeSlug}__${d.dueDate}`;
}

function statusVariant(status: string): "success" | "destructive" | "warning" | "neutral" {
  switch (status) {
    case "filed":
      return "success";
    case "overdue":
      return "destructive";
    case "pending":
      return "warning";
    default:
      return "neutral"; // not_applicable
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case "filed":
      return "Filed";
    case "overdue":
      return "Overdue";
    case "pending":
      return "Pending";
    case "not_applicable":
      return "N/A";
    default:
      return status;
  }
}

export function DeadlineListView({
  deadlines,
  slug,
  selectedIds,
  onSelectionChange,
}: DeadlineListViewProps) {
  const [sortField, setSortField] = useState<SortField>("dueDate");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("asc");
    }
  }

  const sorted = [...deadlines].sort((a, b) => {
    let cmp = 0;
    if (sortField === "dueDate") {
      cmp = a.dueDate.localeCompare(b.dueDate);
    } else if (sortField === "customerName") {
      cmp = a.customerName.localeCompare(b.customerName);
    } else if (sortField === "status") {
      cmp = a.status.localeCompare(b.status);
    }
    return sortDir === "asc" ? cmp : -cmp;
  });

  function toggleAll() {
    if (selectedIds.size === deadlines.length) {
      onSelectionChange(new Set());
    } else {
      onSelectionChange(new Set(deadlines.map(deadlineKey)));
    }
  }

  function toggleOne(key: string) {
    const next = new Set(selectedIds);
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    onSelectionChange(next);
  }

  if (deadlines.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 p-8 text-center dark:border-slate-800">
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No deadlines found for this period.
        </p>
      </div>
    );
  }

  const allSelected = selectedIds.size === deadlines.length && deadlines.length > 0;

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="w-10 px-4 py-3 text-left">
              <Checkbox
                checked={allSelected}
                onCheckedChange={toggleAll}
                aria-label="Select all deadlines"
              />
            </th>
            <th className="px-4 py-3 text-left">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("customerName")}
                className="h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400"
              >
                Client <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Deadline Type
            </th>
            <th className="px-4 py-3 text-left">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("dueDate")}
                className="h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400"
              >
                Due Date <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className="px-4 py-3 text-left">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("status")}
                className="h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400"
              >
                Status <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className="hidden px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase sm:table-cell dark:text-slate-400">
              Linked Engagement
            </th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((deadline) => {
            const key = deadlineKey(deadline);
            const isSelected = selectedIds.has(key);
            return (
              <tr
                key={key}
                className={cn(
                  "group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50",
                  isSelected && "bg-teal-50 dark:bg-teal-950/20"
                )}
              >
                <td className="px-4 py-3">
                  <Checkbox
                    checked={isSelected}
                    onCheckedChange={() => toggleOne(key)}
                    aria-label={`Select ${deadline.customerName} ${deadline.deadlineTypeName}`}
                  />
                </td>
                <td className="px-4 py-3">
                  <Link
                    href={`/org/${slug}/customers/${deadline.customerId}`}
                    className="font-medium text-slate-950 hover:underline dark:text-slate-50"
                  >
                    {deadline.customerName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                  {deadline.deadlineTypeName}
                </td>
                <td className="px-4 py-3 font-mono text-sm text-slate-600 dark:text-slate-400">
                  {deadline.dueDate}
                </td>
                <td className="px-4 py-3">
                  <Badge variant={statusVariant(deadline.status)}>
                    {statusLabel(deadline.status)}
                  </Badge>
                </td>
                <td className="hidden px-4 py-3 text-sm text-slate-600 sm:table-cell dark:text-slate-400">
                  {deadline.linkedProjectId ? (
                    <Link
                      href={`/org/${slug}/projects/${deadline.linkedProjectId}`}
                      className="text-teal-600 hover:underline"
                    >
                      View project
                    </Link>
                  ) : (
                    <span className="text-slate-400">&mdash;</span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
