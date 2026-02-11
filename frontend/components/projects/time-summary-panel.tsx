"use client";

import { useState, useTransition, useCallback } from "react";
import { Clock } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDuration } from "@/lib/format";
import type {
  ProjectTimeSummary,
  MemberTimeSummary,
  TaskTimeSummary,
} from "@/lib/types";
import {
  fetchProjectTimeSummary,
  fetchTimeSummaryByMember,
  fetchTimeSummaryByTask,
} from "@/app/(app)/org/[slug]/projects/[id]/time-summary-actions";

interface TimeSummaryPanelProps {
  projectId: string;
  initialSummary: ProjectTimeSummary;
  initialByTask: TaskTimeSummary[];
  initialByMember: MemberTimeSummary[] | null;
}

export function TimeSummaryPanel({
  projectId,
  initialSummary,
  initialByTask,
  initialByMember,
}: TimeSummaryPanelProps) {
  const [summary, setSummary] = useState(initialSummary);
  const [byTask, setByTask] = useState(initialByTask);
  const [byMember, setByMember] = useState(initialByMember);
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [isPending, startTransition] = useTransition();

  const refetchData = useCallback(
    (from: string, to: string) => {
      startTransition(async () => {
        const fromParam = from || undefined;
        const toParam = to || undefined;
        const [newSummary, newByTask, newByMember] = await Promise.all([
          fetchProjectTimeSummary(projectId, fromParam, toParam),
          fetchTimeSummaryByTask(projectId, fromParam, toParam),
          initialByMember !== null
            ? fetchTimeSummaryByMember(projectId, fromParam, toParam)
            : Promise.resolve(null),
        ]);
        setSummary(newSummary);
        setByTask(newByTask);
        setByMember(newByMember);
      });
    },
    [projectId, initialByMember]
  );

  function handleFromChange(value: string) {
    setFromDate(value);
    refetchData(value, toDate);
  }

  function handleToChange(value: string) {
    setToDate(value);
    refetchData(fromDate, value);
  }

  const isEmpty = summary.totalMinutes === 0 && summary.entryCount === 0;

  return (
    <div className="space-y-6">
      {/* Date Range Picker (47.4) */}
      <div className="flex flex-wrap items-center gap-3">
        <label className="text-sm font-medium text-olive-700 dark:text-olive-300">
          From
        </label>
        <input
          type="date"
          value={fromDate}
          onChange={(e) => handleFromChange(e.target.value)}
          className="rounded-md border border-olive-200 bg-white px-3 py-1.5 text-sm text-olive-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-olive-500 dark:border-olive-700 dark:bg-olive-900 dark:text-olive-100"
        />
        <label className="text-sm font-medium text-olive-700 dark:text-olive-300">
          To
        </label>
        <input
          type="date"
          value={toDate}
          onChange={(e) => handleToChange(e.target.value)}
          className="rounded-md border border-olive-200 bg-white px-3 py-1.5 text-sm text-olive-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-olive-500 dark:border-olive-700 dark:bg-olive-900 dark:text-olive-100"
        />
        {isPending && (
          <span className="text-xs text-olive-500">Loading...</span>
        )}
      </div>

      {/* Empty State */}
      {isEmpty ? (
        <EmptyState
          icon={Clock}
          title="No time tracked yet"
          description="Log time on tasks to see project time summaries here"
        />
      ) : (
        <>
          {/* Total Summary Stat Cards (47.3) */}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            <StatCard label="Total Time" value={formatDuration(summary.totalMinutes)} />
            <StatCard
              label="Billable"
              value={formatDuration(summary.billableMinutes)}
              valueClassName="text-green-600 dark:text-green-400"
            />
            <StatCard
              label="Non-billable"
              value={formatDuration(summary.nonBillableMinutes)}
              valueClassName="text-olive-600 dark:text-olive-400"
            />
            <StatCard label="Contributors" value={String(summary.contributorCount)} />
            <StatCard label="Entries" value={String(summary.entryCount)} />
          </div>

          {/* By Task Breakdown */}
          {byTask.length > 0 && (
            <div className="space-y-3">
              <h3 className="font-semibold text-olive-900 dark:text-olive-100">
                By Task
              </h3>
              <div className="rounded-lg border border-olive-200 dark:border-olive-800">
                <Table>
                  <TableHeader>
                    <TableRow className="border-olive-200 hover:bg-transparent dark:border-olive-800">
                      <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Task
                      </TableHead>
                      <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Billable
                      </TableHead>
                      <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Total
                      </TableHead>
                      <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Entries
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {byTask
                      .slice()
                      .sort((a, b) => b.totalMinutes - a.totalMinutes)
                      .map((task) => (
                        <TableRow
                          key={task.taskId}
                          className="border-olive-100 transition-colors hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900"
                        >
                          <TableCell>
                            <span className="text-sm font-medium text-olive-950 dark:text-olive-50">
                              {task.taskTitle}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span className="text-sm text-green-600 dark:text-green-400">
                              {formatDuration(task.billableMinutes)}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span className="text-sm text-olive-900 dark:text-olive-100">
                              {formatDuration(task.totalMinutes)}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span className="text-sm text-olive-600 dark:text-olive-400">
                              {task.entryCount}
                            </span>
                          </TableCell>
                        </TableRow>
                      ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          )}

          {/* By Member Breakdown (lead+ only) */}
          {byMember !== null && byMember.length > 0 && (
            <div className="space-y-3">
              <h3 className="font-semibold text-olive-900 dark:text-olive-100">
                By Member
              </h3>
              <div className="rounded-lg border border-olive-200 dark:border-olive-800">
                <Table>
                  <TableHeader>
                    <TableRow className="border-olive-200 hover:bg-transparent dark:border-olive-800">
                      <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Member
                      </TableHead>
                      <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Billable
                      </TableHead>
                      <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Non-billable
                      </TableHead>
                      <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Total
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {byMember.map((member) => (
                      <TableRow
                        key={member.memberId}
                        className="border-olive-100 transition-colors hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900"
                      >
                        <TableCell>
                          <span className="text-sm font-medium text-olive-950 dark:text-olive-50">
                            {member.memberName}
                          </span>
                        </TableCell>
                        <TableCell>
                          <span className="text-sm text-green-600 dark:text-green-400">
                            {formatDuration(member.billableMinutes)}
                          </span>
                        </TableCell>
                        <TableCell>
                          <span className="text-sm text-olive-600 dark:text-olive-400">
                            {formatDuration(member.nonBillableMinutes)}
                          </span>
                        </TableCell>
                        <TableCell>
                          <span className="text-sm text-olive-900 dark:text-olive-100">
                            {formatDuration(member.totalMinutes)}
                          </span>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function StatCard({
  label,
  value,
  valueClassName,
}: {
  label: string;
  value: string;
  valueClassName?: string;
}) {
  return (
    <div className="rounded-lg border border-olive-200 bg-white p-4 dark:border-olive-800 dark:bg-olive-950">
      <p className="text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
        {label}
      </p>
      <p
        className={
          valueClassName ??
          "text-olive-950 dark:text-olive-50"
        }
      >
        <span className="font-display text-2xl">{value}</span>
      </p>
    </div>
  );
}
