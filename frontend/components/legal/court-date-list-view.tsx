"use client";

import { useState } from "react";
import { ArrowUpDown, MoreHorizontal } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import type { CourtDate, CourtDateStatus } from "@/lib/types";

interface CourtDateListViewProps {
  courtDates: CourtDate[];
  onEdit: (courtDate: CourtDate) => void;
  onPostpone: (courtDate: CourtDate) => void;
  onCancel: (courtDate: CourtDate) => void;
  onRecordOutcome: (courtDate: CourtDate) => void;
  onSelect: (courtDate: CourtDate) => void;
}

type SortField = "scheduledDate" | "courtName" | "status";
type SortDir = "asc" | "desc";

function statusBadge(status: CourtDateStatus) {
  switch (status) {
    case "SCHEDULED":
      return (
        <Badge className="bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300">
          Scheduled
        </Badge>
      );
    case "POSTPONED":
      return <Badge variant="warning">Postponed</Badge>;
    case "HEARD":
      return <Badge variant="success">Heard</Badge>;
    case "CANCELLED":
      return <Badge variant="neutral">Cancelled</Badge>;
    default:
      return <Badge variant="neutral">{status}</Badge>;
  }
}

function dateTypeLabel(type: string): string {
  return type
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join("-");
}

function canTransition(status: CourtDateStatus): {
  canEdit: boolean;
  canPostpone: boolean;
  canCancel: boolean;
  canOutcome: boolean;
} {
  switch (status) {
    case "SCHEDULED":
      return { canEdit: true, canPostpone: true, canCancel: true, canOutcome: true };
    case "POSTPONED":
      return { canEdit: true, canPostpone: false, canCancel: true, canOutcome: true };
    default:
      return { canEdit: false, canPostpone: false, canCancel: false, canOutcome: false };
  }
}

export function CourtDateListView({
  courtDates,
  onEdit,
  onPostpone,
  onCancel,
  onRecordOutcome,
  onSelect,
}: CourtDateListViewProps) {
  const [sortField, setSortField] = useState<SortField>("scheduledDate");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("asc");
    }
  }

  const sorted = [...courtDates].sort((a, b) => {
    let cmp = 0;
    if (sortField === "scheduledDate") {
      cmp = a.scheduledDate.localeCompare(b.scheduledDate);
    } else if (sortField === "courtName") {
      cmp = a.courtName.localeCompare(b.courtName);
    } else if (sortField === "status") {
      cmp = a.status.localeCompare(b.status);
    }
    return sortDir === "asc" ? cmp : -cmp;
  });

  if (courtDates.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 p-8 text-center dark:border-slate-800">
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No court dates found for this period.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto" data-testid="court-date-list">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="px-4 py-3 text-left">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("scheduledDate")}
                className="h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400"
              >
                Date <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Time
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Type
            </th>
            <th className="px-4 py-3 text-left">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("courtName")}
                className="h-auto p-0 text-xs font-medium tracking-wide text-slate-600 uppercase hover:text-slate-900 dark:text-slate-400"
              >
                Court <ArrowUpDown className="ml-1 size-3" />
              </Button>
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Matter
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Client
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
            <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((cd) => {
            const actions = canTransition(cd.status);
            const hasActions =
              actions.canEdit || actions.canPostpone || actions.canCancel || actions.canOutcome;

            return (
              <tr
                key={cd.id}
                className={cn(
                  "group cursor-pointer border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                )}
                onClick={() => onSelect(cd)}
              >
                <td className="px-4 py-3 font-mono text-sm text-slate-600 dark:text-slate-400">
                  {cd.scheduledDate}
                </td>
                <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                  {cd.scheduledTime ?? "\u2014"}
                </td>
                <td className="px-4 py-3">
                  <Badge variant="outline">{dateTypeLabel(cd.dateType)}</Badge>
                </td>
                <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                  {cd.courtName}
                </td>
                <td className="px-4 py-3 text-sm font-medium text-slate-950 dark:text-slate-50">
                  {cd.projectName}
                </td>
                <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                  {cd.customerName}
                </td>
                <td className="px-4 py-3">{statusBadge(cd.status)}</td>
                <td className="px-4 py-3 text-right">
                  {hasActions && (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="size-8 p-0"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <MoreHorizontal className="size-4" />
                          <span className="sr-only">Actions</span>
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {actions.canEdit && (
                          <DropdownMenuItem
                            onClick={(e) => {
                              e.stopPropagation();
                              onEdit(cd);
                            }}
                          >
                            Edit
                          </DropdownMenuItem>
                        )}
                        {actions.canPostpone && (
                          <DropdownMenuItem
                            onClick={(e) => {
                              e.stopPropagation();
                              onPostpone(cd);
                            }}
                          >
                            Postpone
                          </DropdownMenuItem>
                        )}
                        {actions.canCancel && (
                          <DropdownMenuItem
                            onClick={(e) => {
                              e.stopPropagation();
                              onCancel(cd);
                            }}
                          >
                            Cancel
                          </DropdownMenuItem>
                        )}
                        {actions.canOutcome && (
                          <DropdownMenuItem
                            onClick={(e) => {
                              e.stopPropagation();
                              onRecordOutcome(cd);
                            }}
                          >
                            Record Outcome
                          </DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
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
