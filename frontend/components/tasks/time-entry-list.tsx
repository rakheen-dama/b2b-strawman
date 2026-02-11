"use client";

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
import { EditTimeEntryDialog } from "@/components/tasks/edit-time-entry-dialog";
import { DeleteTimeEntryDialog } from "@/components/tasks/delete-time-entry-dialog";
import { formatDate, formatDuration } from "@/lib/format";
import type { TimeEntry } from "@/lib/types";

/** Org roles that can edit/delete any time entry in the project */
const ELEVATED_ROLES = new Set(["org:admin", "org:owner"]);

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
  const totalMinutes = entries.reduce((sum, e) => sum + e.durationMinutes, 0);

  // Determine if the current user has elevated privileges (lead/admin/owner)
  const isElevated = canManage || (orgRole ? ELEVATED_ROLES.has(orgRole) : false);

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

  // Check if we need an actions column at all
  const showActionsColumn =
    actionsEnabled && entries.some((e) => canEditEntry(e));

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <h3 className="text-sm font-semibold text-olive-900 dark:text-olive-100">
          Time Entries
        </h3>
        <Badge variant="neutral">{formatDuration(totalMinutes)}</Badge>
      </div>
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
                Billable
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
            {entries.map((entry) => {
              const editable = canEditEntry(entry);

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
                    {entry.billable ? (
                      <Badge variant="success">Billable</Badge>
                    ) : (
                      <Badge variant="neutral">Non-billable</Badge>
                    )}
                  </TableCell>
                  <TableCell className="hidden max-w-[200px] truncate text-sm text-olive-500 sm:table-cell dark:text-olive-500">
                    {entry.description ?? "\u2014"}
                  </TableCell>
                  {showActionsColumn && (
                    <TableCell>
                      {editable && slug && projectId && (
                        <div className="flex items-center gap-1">
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
                        </div>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
