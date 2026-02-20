"use client";

import { useState } from "react";
import { CalendarClock, Pause, Play, Trash2, Eye } from "lucide-react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  pauseScheduleAction,
  resumeScheduleAction,
  deleteScheduleAction,
} from "@/app/(app)/org/[slug]/schedules/actions";
import { formatDate } from "@/lib/format";
import type {
  ScheduleResponse,
  ScheduleStatus,
  RecurrenceFrequency,
} from "@/lib/api/schedules";

const FREQUENCY_LABELS: Record<RecurrenceFrequency, string> = {
  WEEKLY: "Weekly",
  FORTNIGHTLY: "Fortnightly",
  MONTHLY: "Monthly",
  QUARTERLY: "Quarterly",
  SEMI_ANNUALLY: "Semi-Annually",
  ANNUALLY: "Annually",
};

const STATUS_TABS: { label: string; value: ScheduleStatus | "ALL" }[] = [
  { label: "Active", value: "ACTIVE" },
  { label: "Paused", value: "PAUSED" },
  { label: "Completed", value: "COMPLETED" },
  { label: "All", value: "ALL" },
];

interface ScheduleListProps {
  slug: string;
  schedules: ScheduleResponse[];
}

export function ScheduleList({ slug, schedules }: ScheduleListProps) {
  const [activeTab, setActiveTab] = useState<ScheduleStatus | "ALL">("ACTIVE");
  const [errorMessages, setErrorMessages] = useState<Record<string, string>>({});
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  const filtered =
    activeTab === "ALL"
      ? schedules
      : schedules.filter((s) => s.status === activeTab);

  async function handlePause(id: string) {
    if (actionInProgress) return;
    setActionInProgress(id);
    try {
      const result = await pauseScheduleAction(slug, id);
      if (!result.success && result.error) {
        setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
      }
    } finally {
      setActionInProgress(null);
    }
  }

  async function handleResume(id: string) {
    if (actionInProgress) return;
    setActionInProgress(id);
    try {
      const result = await resumeScheduleAction(slug, id);
      if (!result.success && result.error) {
        setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
      }
    } finally {
      setActionInProgress(null);
    }
  }

  async function handleDelete(id: string) {
    if (actionInProgress) return;
    setActionInProgress(id);
    try {
      const result = await deleteScheduleAction(slug, id);
      if (!result.success && result.error) {
        setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
      }
    } finally {
      setActionInProgress(null);
    }
  }

  function statusBadge(status: ScheduleStatus) {
    switch (status) {
      case "ACTIVE":
        return <Badge variant="success">Active</Badge>;
      case "PAUSED":
        return <Badge variant="warning">Paused</Badge>;
      case "COMPLETED":
        return <Badge variant="neutral">Completed</Badge>;
    }
  }

  return (
    <div className="space-y-4">
      {/* Tabs */}
      <div className="flex gap-1 rounded-lg border border-slate-200 bg-slate-50 p-1 dark:border-slate-800 dark:bg-slate-900">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value}
            onClick={() => setActiveTab(tab.value)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              activeTab === tab.value
                ? "bg-white text-slate-900 shadow-sm dark:bg-slate-800 dark:text-slate-100"
                : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Content */}
      {filtered.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <CalendarClock className="size-12 text-slate-300 dark:text-slate-700" />
          <h2 className="mt-4 font-display text-lg text-slate-900 dark:text-slate-100">
            No {activeTab === "ALL" ? "" : activeTab.toLowerCase() + " "}schedules found.
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {activeTab === "ACTIVE"
              ? "Create a recurring schedule to automate project creation."
              : activeTab === "PAUSED"
                ? "No schedules are currently paused."
                : activeTab === "COMPLETED"
                  ? "No schedules have completed yet."
                  : "Create your first recurring schedule to get started."}
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Template
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Customer
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Frequency
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Next Execution
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Last Executed
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Executions
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Status
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((schedule) => (
                <tr
                  key={schedule.id}
                  className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <p className="font-medium text-slate-900 dark:text-slate-100">
                      {schedule.templateName}
                    </p>
                    {schedule.nameOverride && (
                      <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                        {schedule.nameOverride}
                      </p>
                    )}
                    {errorMessages[schedule.id] && (
                      <p className="mt-1 text-xs text-red-600 dark:text-red-400">
                        {errorMessages[schedule.id]}
                      </p>
                    )}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {schedule.customerName}
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant="neutral">
                      {FREQUENCY_LABELS[schedule.frequency]}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {schedule.nextExecutionDate
                      ? formatDate(schedule.nextExecutionDate)
                      : "\u2014"}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {schedule.lastExecutedAt
                      ? formatDate(schedule.lastExecutedAt)
                      : "\u2014"}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {schedule.executionCount}
                  </td>
                  <td className="px-4 py-3">{statusBadge(schedule.status)}</td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Link href={`/org/${slug}/schedules/${schedule.id}`}>
                        <Button variant="ghost" size="sm" title="View schedule">
                          <Eye className="size-4" />
                          <span className="sr-only">View {schedule.templateName}</span>
                        </Button>
                      </Link>

                      {schedule.status === "ACTIVE" && (
                        <Button
                          variant="ghost"
                          size="sm"
                          title="Pause schedule"
                          onClick={() => handlePause(schedule.id)}
                          disabled={actionInProgress === schedule.id}
                        >
                          <Pause className="size-4" />
                          <span className="sr-only">Pause {schedule.templateName}</span>
                        </Button>
                      )}

                      {schedule.status === "PAUSED" && (
                        <Button
                          variant="ghost"
                          size="sm"
                          title="Resume schedule"
                          onClick={() => handleResume(schedule.id)}
                          disabled={actionInProgress === schedule.id}
                        >
                          <Play className="size-4" />
                          <span className="sr-only">Resume {schedule.templateName}</span>
                        </Button>
                      )}

                      {(schedule.status === "PAUSED" || schedule.status === "COMPLETED") && (
                        <AlertDialog>
                          <AlertDialogTrigger asChild>
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
                              title="Delete schedule"
                            >
                              <Trash2 className="size-4" />
                              <span className="sr-only">Delete {schedule.templateName}</span>
                            </Button>
                          </AlertDialogTrigger>
                          <AlertDialogContent>
                            <AlertDialogHeader>
                              <AlertDialogTitle>Delete Schedule</AlertDialogTitle>
                              <AlertDialogDescription>
                                Are you sure you want to delete the schedule for &quot;
                                {schedule.templateName}&quot; ({schedule.customerName})? This
                                action cannot be undone.
                              </AlertDialogDescription>
                            </AlertDialogHeader>
                            <AlertDialogFooter>
                              <AlertDialogCancel>Cancel</AlertDialogCancel>
                              <AlertDialogAction
                                className="bg-red-600 hover:bg-red-700"
                                onClick={() => handleDelete(schedule.id)}
                                disabled={actionInProgress === schedule.id}
                              >
                                {actionInProgress === schedule.id ? "Deleting..." : "Delete"}
                              </AlertDialogAction>
                            </AlertDialogFooter>
                          </AlertDialogContent>
                        </AlertDialog>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
