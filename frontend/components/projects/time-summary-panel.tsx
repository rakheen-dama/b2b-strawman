"use client";

import { useEffect, useState, useTransition } from "react";
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
import type { MatterTimeSummary, MemberTimeSummary, TaskTimeSummary } from "@/lib/types";
import {
  fetchProjectTimeSummary,
  fetchTimeSummaryByMember,
  fetchTimeSummaryByTask,
} from "@/app/(app)/org/[slug]/matters/[id]/time-summary-actions";

interface FilteredTimeData {
  summary: MatterTimeSummary;
  byTask: TaskTimeSummary[];
  byMember: MemberTimeSummary[] | null;
}

interface TimeSummaryPanelProps {
  projectId: string;
  initialSummary: MatterTimeSummary;
  initialByTask: TaskTimeSummary[];
  initialByMember: MemberTimeSummary[] | null;
}

export function TimeSummaryPanel({
  projectId,
  initialSummary,
  initialByTask,
  initialByMember,
}: TimeSummaryPanelProps) {
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  // Data fetched for the active date filter only. With no filter the panel renders the server
  // props directly, so a page refresh after a time entry is logged (revalidatePath) shows at once
  // even when this tab mounted before the refresh committed (F-657A-1). Copying the props into
  // state kept the mount-time values until a reload.
  const [filtered, setFiltered] = useState<FilteredTimeData | null>(null);
  const [isPending, startTransition] = useTransition();
  const hasFilter = fromDate !== "" || toDate !== "";
  const withMembers = initialByMember !== null;

  // Re-fetch the filtered view when the filter changes, and also when the server data changes
  // underneath it (a new entry), so a filtered view never lags the page either. Responses that
  // arrive after a newer request started are dropped.
  useEffect(() => {
    if (!hasFilter) return;
    let cancelled = false;
    startTransition(async () => {
      const fromParam = fromDate || undefined;
      const toParam = toDate || undefined;
      const [summary, byTask, byMember] = await Promise.all([
        fetchProjectTimeSummary(projectId, fromParam, toParam),
        fetchTimeSummaryByTask(projectId, fromParam, toParam),
        withMembers
          ? fetchTimeSummaryByMember(projectId, fromParam, toParam)
          : Promise.resolve(null),
      ]);
      if (!cancelled) setFiltered({ summary, byTask, byMember });
    });
    return () => {
      cancelled = true;
    };
  }, [
    hasFilter,
    fromDate,
    toDate,
    projectId,
    withMembers,
    initialSummary,
    initialByTask,
    initialByMember,
  ]);

  const { summary, byTask, byMember } =
    hasFilter && filtered
      ? filtered
      : { summary: initialSummary, byTask: initialByTask, byMember: initialByMember };

  function handleFromChange(value: string) {
    setFromDate(value);
  }

  function handleToChange(value: string) {
    setToDate(value);
  }

  const isEmpty = summary.totalMinutes === 0 && summary.entryCount === 0;

  return (
    <div className="space-y-6">
      {/* Date Range Picker (47.4) */}
      <div className="flex flex-wrap items-center gap-3">
        <label className="text-sm font-medium text-slate-700 dark:text-slate-300">From</label>
        <input
          type="date"
          value={fromDate}
          onChange={(e) => handleFromChange(e.target.value)}
          className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-900 focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
        />
        <label className="text-sm font-medium text-slate-700 dark:text-slate-300">To</label>
        <input
          type="date"
          value={toDate}
          onChange={(e) => handleToChange(e.target.value)}
          className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-900 focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
        />
        {isPending && <span className="text-xs text-slate-500">Loading...</span>}
      </div>

      {/* Empty State */}
      {isEmpty ? (
        <EmptyState
          icon={Clock}
          title="No time tracked yet"
          description="Log time on tasks to see matter time summaries here"
        />
      ) : (
        <>
          {/* Total Summary Stat Cards (47.3) */}
          <div className="grid auto-rows-fr grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            <StatCard label="Total Time" value={formatDuration(summary.totalMinutes)} />
            <StatCard
              label="Billable"
              value={formatDuration(summary.billableMinutes)}
              valueClassName="text-green-600 dark:text-green-400"
            />
            <StatCard
              label="Non-billable"
              value={formatDuration(summary.nonBillableMinutes)}
              valueClassName="text-slate-600 dark:text-slate-400"
            />
            <StatCard label="Contributors" value={String(summary.contributorCount)} />
            <StatCard label="Entries" value={String(summary.entryCount)} />
          </div>

          {/* By Task Breakdown */}
          {byTask.length > 0 && (
            <div className="space-y-3">
              <h3 className="font-semibold text-slate-900 dark:text-slate-100">By Task</h3>
              <div className="rounded-lg border border-slate-200 dark:border-slate-800">
                <Table>
                  <TableHeader>
                    <TableRow className="border-slate-200 hover:bg-transparent dark:border-slate-800">
                      <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
                        Task
                      </TableHead>
                      <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
                        Billable
                      </TableHead>
                      <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
                        Total
                      </TableHead>
                      <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
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
                          className="border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900"
                        >
                          <TableCell>
                            <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                              {task.taskTitle}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span className="text-sm text-green-600 dark:text-green-400">
                              {formatDuration(task.billableMinutes)}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span className="text-sm text-slate-900 dark:text-slate-100">
                              {formatDuration(task.totalMinutes)}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span className="text-sm text-slate-600 dark:text-slate-400">
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
              <h3 className="font-semibold text-slate-900 dark:text-slate-100">By Member</h3>
              <div className="rounded-lg border border-slate-200 dark:border-slate-800">
                <Table>
                  <TableHeader>
                    <TableRow className="border-slate-200 hover:bg-transparent dark:border-slate-800">
                      <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
                        Member
                      </TableHead>
                      <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
                        Billable
                      </TableHead>
                      <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
                        Non-billable
                      </TableHead>
                      <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
                        Total
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {byMember.map((member) => (
                      <TableRow
                        key={member.memberId}
                        className="border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900"
                      >
                        <TableCell>
                          <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                            {member.memberName}
                          </span>
                        </TableCell>
                        <TableCell>
                          <span className="text-sm text-green-600 dark:text-green-400">
                            {formatDuration(member.billableMinutes)}
                          </span>
                        </TableCell>
                        <TableCell>
                          <span className="text-sm text-slate-600 dark:text-slate-400">
                            {formatDuration(member.nonBillableMinutes)}
                          </span>
                        </TableCell>
                        <TableCell>
                          <span className="text-sm text-slate-900 dark:text-slate-100">
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
    <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
      <p className="text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
        {label}
      </p>
      <p className={valueClassName ?? "text-slate-950 dark:text-slate-50"}>
        <span className="font-display text-2xl">{value}</span>
      </p>
    </div>
  );
}
