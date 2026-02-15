"use client";

import { useState } from "react";
import { Clock, Pencil, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { EditTimeEntryDialog } from "@/components/tasks/edit-time-entry-dialog";
import { DeleteTimeEntryDialog } from "@/components/tasks/delete-time-entry-dialog";
import { BillingStatusBadge } from "@/components/time-entries/billing-status-badge";
import { formatCurrencySafe, formatDate, formatDuration } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { TimeEntry } from "@/lib/types";

/** Org roles that can edit/delete any time entry in the project */
const ELEVATED_ROLES = new Set(["org:admin", "org:owner"]);

type BillingStatusFilter = "all" | "unbilled" | "billed" | "non-billable";

const BILLING_STATUS_FILTER_OPTIONS: {
  key: BillingStatusFilter;
  label: string;
}[] = [
  { key: "all", label: "All" },
  { key: "unbilled", label: "Unbilled" },
  { key: "billed", label: "Billed" },
  { key: "non-billable", label: "Non-billable" },
];

interface TimeEntryListProps {
  entries: TimeEntry[];
  slug?: string;
  projectId?: string;
  currentMemberId?: string | null;
  orgRole?: string | null;
  /** Whether the current user can manage the project (lead, admin, or owner) */
  canManage?: boolean;
}

export function TimeEntryList({
  entries,
  slug,
  projectId,
  currentMemberId,
  orgRole,
  canManage = false,
}: TimeEntryListProps) {
  const [billingFilter, setBillingFilter] =
    useState<BillingStatusFilter>("all");

  // Apply client-side billing status filter
  const filteredEntries =
    billingFilter === "all"
      ? entries
      : billingFilter === "unbilled"
        ? entries.filter((e) => e.billable && !e.invoiceId)
        : billingFilter === "billed"
          ? entries.filter((e) => !!e.invoiceId)
          : entries.filter((e) => !e.billable);

  const totalMinutes = filteredEntries.reduce(
    (sum, e) => sum + e.durationMinutes,
    0,
  );

  // Determine if the current user has elevated privileges (lead/admin/owner)
  const isElevated =
    canManage || (orgRole ? ELEVATED_ROLES.has(orgRole) : false);

  // Whether any actions are possible (need slug + projectId to wire up actions)
  const actionsEnabled = !!slug && !!projectId;

  function canEditEntry(entry: TimeEntry): boolean {
    if (!actionsEnabled) return false;
    if (isElevated) return true;
    if (currentMemberId && entry.memberId === currentMemberId) return true;
    return false;
  }

  if (entries.length === 0) {
    return (
      <EmptyState
        icon={Clock}
        title="No time logged yet"
        description="Use the Log Time button to record time spent on this task"
      />
    );
  }

  // Check if we need an actions column: show if any entry is editable by the current user
  const showActionsColumn =
    actionsEnabled &&
    filteredEntries.some((e) => canEditEntry(e));

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold text-olive-900 dark:text-olive-100">
            Time Entries
          </h3>
          <Badge variant="neutral">{formatDuration(totalMinutes)}</Badge>
        </div>
      </div>

      {/* Billing status filter toggle */}
      <div
        className="flex flex-wrap gap-2"
        role="group"
        aria-label="Billing status filter"
      >
        {BILLING_STATUS_FILTER_OPTIONS.map((option) => (
          <button
            key={option.key}
            type="button"
            onClick={() => setBillingFilter(option.key)}
            className={cn(
              "rounded-full px-3 py-1 text-sm font-medium transition-colors",
              billingFilter === option.key
                ? "bg-olive-900 text-olive-50 dark:bg-olive-100 dark:text-olive-900"
                : "bg-olive-100 text-olive-600 hover:bg-olive-200 dark:bg-olive-800 dark:text-olive-400 dark:hover:bg-olive-700",
            )}
          >
            {option.label}
          </button>
        ))}
      </div>

      {filteredEntries.length === 0 ? (
        <p className="py-4 text-center text-sm text-olive-500 dark:text-olive-400">
          No{" "}
          {billingFilter === "all"
            ? ""
            : billingFilter === "unbilled"
              ? "unbilled "
              : billingFilter === "billed"
                ? "billed "
                : "non-billable "}
          time entries.
        </p>
      ) : (
        <div className="rounded-lg border border-olive-200 dark:border-olive-800">
          <Table>
            <TableHeader>
              <TableRow className="border-olive-200 hover:bg-transparent dark:border-olive-800">
                <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Date
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Duration
                </TableHead>
                <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                  Member
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Billing
                </TableHead>
                <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                  Value
                </TableHead>
                <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                  Description
                </TableHead>
                {showActionsColumn && (
                  <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                    Actions
                  </TableHead>
                )}
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredEntries.map((entry) => {
                const editable = canEditEntry(entry);
                const isBilled = !!entry.invoiceId;

                return (
                  <TableRow
                    key={entry.id}
                    className="border-olive-100 transition-colors hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900"
                  >
                    <TableCell className="text-sm text-olive-600 dark:text-olive-400">
                      {formatDate(entry.date)}
                    </TableCell>
                    <TableCell className="text-sm font-medium text-olive-950 dark:text-olive-50">
                      {formatDuration(entry.durationMinutes)}
                    </TableCell>
                    <TableCell className="hidden text-sm text-olive-600 sm:table-cell dark:text-olive-400">
                      {entry.memberName}
                    </TableCell>
                    <TableCell>
                      <BillingStatusBadge
                        billable={entry.billable}
                        invoiceId={entry.invoiceId}
                        invoiceNumber={entry.invoiceNumber}
                        slug={slug}
                      />
                    </TableCell>
                    <TableCell className="hidden text-sm text-olive-600 sm:table-cell dark:text-olive-400">
                      {entry.billableValue != null &&
                      entry.billingRateCurrency ? (
                        <span className="font-medium text-olive-700 dark:text-olive-300">
                          {formatCurrencySafe(
                            entry.billableValue,
                            entry.billingRateCurrency,
                          )}
                        </span>
                      ) : (
                        "\u2014"
                      )}
                    </TableCell>
                    <TableCell className="hidden max-w-[200px] truncate text-sm text-olive-500 sm:table-cell dark:text-olive-500">
                      {entry.description ?? "\u2014"}
                    </TableCell>
                    {showActionsColumn && (
                      <TableCell>
                        {editable && slug && projectId && (
                          <TooltipProvider>
                            <div className="flex items-center gap-1">
                              {isBilled ? (
                                <>
                                  <Tooltip>
                                    <TooltipTrigger asChild>
                                      <span>
                                        <Button
                                          size="xs"
                                          variant="ghost"
                                          disabled
                                          aria-label={`Edit time entry by ${entry.memberName}`}
                                        >
                                          <Pencil className="size-3" />
                                        </Button>
                                      </span>
                                    </TooltipTrigger>
                                    <TooltipContent>
                                      <p>
                                        Time entry is part of invoice{" "}
                                        {entry.invoiceNumber}. Void the invoice
                                        to unlock.
                                      </p>
                                    </TooltipContent>
                                  </Tooltip>
                                  <Tooltip>
                                    <TooltipTrigger asChild>
                                      <span>
                                        <Button
                                          size="xs"
                                          variant="ghost"
                                          disabled
                                          aria-label={`Delete time entry by ${entry.memberName}`}
                                        >
                                          <Trash2 className="size-3 text-red-500" />
                                        </Button>
                                      </span>
                                    </TooltipTrigger>
                                    <TooltipContent>
                                      <p>
                                        Time entry is part of invoice{" "}
                                        {entry.invoiceNumber}. Void the invoice
                                        to unlock.
                                      </p>
                                    </TooltipContent>
                                  </Tooltip>
                                </>
                              ) : (
                                <>
                                  <EditTimeEntryDialog
                                    entry={entry}
                                    slug={slug}
                                    projectId={projectId}
                                  >
                                    <Button
                                      size="xs"
                                      variant="ghost"
                                      aria-label={`Edit time entry by ${entry.memberName}`}
                                    >
                                      <Pencil className="size-3" />
                                    </Button>
                                  </EditTimeEntryDialog>
                                  <DeleteTimeEntryDialog
                                    slug={slug}
                                    projectId={projectId}
                                    timeEntryId={entry.id}
                                  >
                                    <Button
                                      size="xs"
                                      variant="ghost"
                                      aria-label={`Delete time entry by ${entry.memberName}`}
                                    >
                                      <Trash2 className="size-3 text-red-500" />
                                    </Button>
                                  </DeleteTimeEntryDialog>
                                </>
                              )}
                            </div>
                          </TooltipProvider>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
