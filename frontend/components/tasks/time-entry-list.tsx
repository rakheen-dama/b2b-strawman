"use client";

import { Clock } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate, formatDuration } from "@/lib/format";
import type { TimeEntry } from "@/lib/types";

interface TimeEntryListProps {
  entries: TimeEntry[];
}

export function TimeEntryList({ entries }: TimeEntryListProps) {
  const totalMinutes = entries.reduce((sum, e) => sum + e.durationMinutes, 0);

  if (entries.length === 0) {
    return (
      <EmptyState
        icon={Clock}
        title="No time logged yet"
        description="Use the Log Time button to record time spent on this task"
      />
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <h3 className="text-sm font-semibold text-olive-900 dark:text-olive-100">Time Entries</h3>
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
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.map((entry) => (
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
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
