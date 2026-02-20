"use client";

import { useState } from "react";
import { FileText, Pause, Play, XCircle, ArrowUpDown } from "lucide-react";
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
} from "@/components/ui/alert-dialog";
import {
  pauseRetainerAction,
  resumeRetainerAction,
  terminateRetainerAction,
} from "@/app/(app)/org/[slug]/retainers/actions";
import type {
  RetainerStatus,
  RetainerType,
  RetainerFrequency,
  PeriodStatus,
  RetainerResponse,
} from "@/lib/api/retainers";

// Value constants kept local — cannot import from "server-only" module
const FREQUENCY_LABELS: Record<RetainerFrequency, string> = {
  WEEKLY: "Weekly",
  FORTNIGHTLY: "Fortnightly",
  MONTHLY: "Monthly",
  QUARTERLY: "Quarterly",
  SEMI_ANNUALLY: "Semi-annually",
  ANNUALLY: "Annually",
};

const TYPE_LABELS: Record<RetainerType, string> = {
  HOUR_BANK: "Hour Bank",
  FIXED_FEE: "Fixed Fee",
};

const STATUS_TABS: { label: string; value: RetainerStatus | "ALL" }[] = [
  { label: "Active", value: "ACTIVE" },
  { label: "Paused", value: "PAUSED" },
  { label: "Terminated", value: "TERMINATED" },
  { label: "All", value: "ALL" },
];

interface RetainerListProps {
  slug: string;
  retainers: RetainerResponse[];
}

export function RetainerList({ slug, retainers }: RetainerListProps) {
  const [activeTab, setActiveTab] = useState<RetainerStatus | "ALL">("ACTIVE");
  const [errorMessages, setErrorMessages] = useState<Record<string, string>>({});
  const [pauseDialogId, setPauseDialogId] = useState<string | null>(null);
  const [isPausingId, setIsPausingId] = useState<string | null>(null);
  const [resumeDialogId, setResumeDialogId] = useState<string | null>(null);
  const [isResumingId, setIsResumingId] = useState<string | null>(null);
  const [terminateDialogId, setTerminateDialogId] = useState<string | null>(null);
  const [isTerminatingId, setIsTerminatingId] = useState<string | null>(null);
  const [sortAsc, setSortAsc] = useState(true);

  const filtered =
    activeTab === "ALL"
      ? retainers
      : retainers.filter((r) => r.status === activeTab);

  const sorted = [...filtered].sort((a, b) =>
    sortAsc ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name),
  );

  async function handlePause(id: string) {
    setIsPausingId(id);
    try {
      const result = await pauseRetainerAction(slug, id);
      if (!result.success && result.error) {
        setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
      }
      setPauseDialogId(null);
    } catch {
      setErrorMessages((prev) => ({ ...prev, [id]: "An unexpected error occurred." }));
    } finally {
      setIsPausingId(null);
    }
  }

  async function handleResume(id: string) {
    setIsResumingId(id);
    try {
      const result = await resumeRetainerAction(slug, id);
      if (!result.success && result.error) {
        setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
      }
      setResumeDialogId(null);
    } catch {
      setErrorMessages((prev) => ({ ...prev, [id]: "An unexpected error occurred." }));
    } finally {
      setIsResumingId(null);
    }
  }

  async function handleTerminate(id: string) {
    setIsTerminatingId(id);
    try {
      const result = await terminateRetainerAction(slug, id);
      if (!result.success && result.error) {
        setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
      }
      setTerminateDialogId(null);
    } catch {
      setErrorMessages((prev) => ({ ...prev, [id]: "An unexpected error occurred." }));
    } finally {
      setIsTerminatingId(null);
    }
  }

  function retainerStatusBadge(status: RetainerStatus) {
    switch (status) {
      case "ACTIVE":
        return <Badge variant="success">Active</Badge>;
      case "PAUSED":
        return <Badge variant="warning">Paused</Badge>;
      case "TERMINATED":
        return <Badge variant="neutral">Terminated</Badge>;
    }
  }

  function periodStatusBadge(status: PeriodStatus) {
    switch (status) {
      case "OPEN":
        return <Badge variant="lead">Open</Badge>;
      case "CLOSED":
        return <Badge variant="neutral">Closed</Badge>;
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
      {sorted.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <FileText className="size-12 text-slate-300 dark:text-slate-700" />
          <h2 className="mt-4 font-display text-lg text-slate-900 dark:text-slate-100">
            No retainers found.
          </h2>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  <button
                    onClick={() => setSortAsc(!sortAsc)}
                    className="inline-flex items-center gap-1"
                  >
                    Agreement
                    <ArrowUpDown className="size-3" />
                  </button>
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Customer
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Type
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Frequency
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Period
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Hours
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
              {sorted.map((retainer) => (
                <tr
                  key={retainer.id}
                  className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/org/${slug}/retainers/${retainer.id}`}
                      className="font-medium text-slate-900 hover:text-teal-600 dark:text-slate-100 dark:hover:text-teal-400"
                    >
                      {retainer.name}
                    </Link>
                    {errorMessages[retainer.id] && (
                      <p className="mt-1 text-xs text-red-600 dark:text-red-400">
                        {errorMessages[retainer.id]}
                      </p>
                    )}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {retainer.customerName}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {TYPE_LABELS[retainer.type]}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {FREQUENCY_LABELS[retainer.frequency]}
                  </td>
                  <td className="px-4 py-3">
                    {retainer.currentPeriod
                      ? periodStatusBadge(retainer.currentPeriod.status)
                      : <span className="text-sm text-slate-400">—</span>}
                  </td>
                  <td className="min-w-[140px] px-4 py-3">
                    {retainer.type === "HOUR_BANK" && retainer.currentPeriod ? (
                      <div className="space-y-1">
                        <div className="flex justify-between text-xs text-slate-600 dark:text-slate-400">
                          <span>{retainer.currentPeriod.consumedHours.toFixed(1)}h</span>
                          <span>{retainer.currentPeriod.allocatedHours.toFixed(1)}h</span>
                        </div>
                        <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                          <div
                            className={`h-full rounded-full transition-all ${
                              retainer.currentPeriod.overageHours > 0
                                ? "bg-amber-500"
                                : "bg-teal-500"
                            }`}
                            style={{
                              width: `${Math.min(
                                100,
                                retainer.currentPeriod.allocatedHours > 0
                                  ? (retainer.currentPeriod.consumedHours / retainer.currentPeriod.allocatedHours) * 100
                                  : 0,
                              )}%`,
                            }}
                          />
                        </div>
                      </div>
                    ) : (
                      <span className="text-sm text-slate-400">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {retainerStatusBadge(retainer.status)}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-1">
                      {retainer.status === "ACTIVE" && (
                        <>
                          <Button
                            variant="ghost"
                            size="sm"
                            title="Pause retainer"
                            onClick={() => setPauseDialogId(retainer.id)}
                            disabled={isPausingId === retainer.id}
                          >
                            <Pause className="size-4" />
                            <span className="sr-only">Pause {retainer.name}</span>
                          </Button>
                          <AlertDialog
                            open={pauseDialogId === retainer.id}
                            onOpenChange={(open) => {
                              if (!open && isPausingId !== retainer.id) setPauseDialogId(null);
                            }}
                          >
                            <AlertDialogContent>
                              <AlertDialogHeader>
                                <AlertDialogTitle>Pause Retainer</AlertDialogTitle>
                                <AlertDialogDescription>
                                  Pausing this retainer will suspend time tracking and billing
                                  for &quot;{retainer.name}&quot;.
                                </AlertDialogDescription>
                              </AlertDialogHeader>
                              <AlertDialogFooter>
                                <AlertDialogCancel disabled={isPausingId === retainer.id}>
                                  Cancel
                                </AlertDialogCancel>
                                <AlertDialogAction
                                  onClick={() => handlePause(retainer.id)}
                                  disabled={isPausingId === retainer.id}
                                >
                                  {isPausingId === retainer.id ? "Pausing..." : "Pause Retainer"}
                                </AlertDialogAction>
                              </AlertDialogFooter>
                            </AlertDialogContent>
                          </AlertDialog>
                        </>
                      )}
                      {retainer.status === "PAUSED" && (
                        <>
                          <Button
                            variant="ghost"
                            size="sm"
                            title="Resume retainer"
                            onClick={() => setResumeDialogId(retainer.id)}
                            disabled={isResumingId === retainer.id}
                          >
                            <Play className="size-4" />
                            <span className="sr-only">Resume {retainer.name}</span>
                          </Button>
                          <AlertDialog
                            open={resumeDialogId === retainer.id}
                            onOpenChange={(open) => {
                              if (!open && isResumingId !== retainer.id) setResumeDialogId(null);
                            }}
                          >
                            <AlertDialogContent>
                              <AlertDialogHeader>
                                <AlertDialogTitle>Resume Retainer</AlertDialogTitle>
                                <AlertDialogDescription>
                                  Resuming this retainer will reactivate time tracking and billing
                                  for &quot;{retainer.name}&quot;.
                                </AlertDialogDescription>
                              </AlertDialogHeader>
                              <AlertDialogFooter>
                                <AlertDialogCancel disabled={isResumingId === retainer.id}>
                                  Cancel
                                </AlertDialogCancel>
                                <AlertDialogAction
                                  onClick={() => handleResume(retainer.id)}
                                  disabled={isResumingId === retainer.id}
                                >
                                  {isResumingId === retainer.id ? "Resuming..." : "Resume Retainer"}
                                </AlertDialogAction>
                              </AlertDialogFooter>
                            </AlertDialogContent>
                          </AlertDialog>
                        </>
                      )}
                      {(retainer.status === "ACTIVE" || retainer.status === "PAUSED") && (
                        <>
                          <Button
                            variant="ghost"
                            size="sm"
                            title="Terminate retainer"
                            onClick={() => setTerminateDialogId(retainer.id)}
                            disabled={isTerminatingId === retainer.id}
                          >
                            <XCircle className="size-4 text-red-500" />
                            <span className="sr-only">Terminate {retainer.name}</span>
                          </Button>
                          <AlertDialog
                            open={terminateDialogId === retainer.id}
                            onOpenChange={(open) => {
                              if (!open && isTerminatingId !== retainer.id) setTerminateDialogId(null);
                            }}
                          >
                            <AlertDialogContent>
                              <AlertDialogHeader>
                                <AlertDialogTitle>Terminate Retainer</AlertDialogTitle>
                                <AlertDialogDescription>
                                  This will permanently terminate &quot;{retainer.name}&quot;.
                                  This action cannot be undone.
                                </AlertDialogDescription>
                              </AlertDialogHeader>
                              <AlertDialogFooter>
                                <AlertDialogCancel disabled={isTerminatingId === retainer.id}>
                                  Cancel
                                </AlertDialogCancel>
                                <AlertDialogAction
                                  onClick={() => handleTerminate(retainer.id)}
                                  disabled={isTerminatingId === retainer.id}
                                  className="bg-red-600 text-white hover:bg-red-700"
                                >
                                  {isTerminatingId === retainer.id ? "Terminating..." : "Terminate Retainer"}
                                </AlertDialogAction>
                              </AlertDialogFooter>
                            </AlertDialogContent>
                          </AlertDialog>
                        </>
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
