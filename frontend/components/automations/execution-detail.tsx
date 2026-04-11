"use client";

import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { ExecutionStatusBadge } from "@/components/automations/execution-status-badge";
import { TriggerTypeBadge } from "@/components/automations/trigger-type-badge";
import { computeDuration } from "@/lib/format";
import { RelativeDate } from "@/components/ui/relative-date";
import { Check, X, ChevronDown } from "lucide-react";
import { useState } from "react";
import type {
  AutomationExecutionResponse,
  ActionExecutionStatus,
  TriggerType,
} from "@/lib/api/automations";

const ACTION_STATUS_CONFIG: Record<
  ActionExecutionStatus,
  { label: string; variant: "success" | "destructive" | "neutral" | "warning" }
> = {
  PENDING: { label: "Pending", variant: "warning" },
  COMPLETED: { label: "Completed", variant: "success" },
  FAILED: { label: "Failed", variant: "destructive" },
  SKIPPED: { label: "Skipped", variant: "neutral" },
};

function formatActionType(type: string): string {
  return type
    .split("_")
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(" ");
}

interface ExecutionDetailProps {
  execution: AutomationExecutionResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ExecutionDetail({ execution, open, onOpenChange }: ExecutionDetailProps) {
  const [expandedErrors, setExpandedErrors] = useState<Set<string>>(new Set());

  function toggleError(actionId: string) {
    setExpandedErrors((prev) => {
      const next = new Set(prev);
      if (next.has(actionId)) {
        next.delete(actionId);
      } else {
        next.add(actionId);
      }
      return next;
    });
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Execution Detail</SheetTitle>
          <SheetDescription>
            {execution ? `Execution of "${execution.ruleName}"` : "Loading..."}
          </SheetDescription>
        </SheetHeader>

        {execution && (
          <div className="mt-6 space-y-6">
            {/* Overview */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  Status
                </span>
                <ExecutionStatusBadge status={execution.status} />
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  Trigger
                </span>
                <TriggerTypeBadge triggerType={execution.triggerEventType as TriggerType} />
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  Conditions Met
                </span>
                {execution.conditionsMet ? (
                  <Check className="size-4 text-emerald-600" />
                ) : (
                  <X className="size-4 text-red-500" />
                )}
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  Duration
                </span>
                <span className="font-mono text-sm text-slate-600 tabular-nums dark:text-slate-400">
                  {computeDuration(execution.startedAt, execution.completedAt)}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  Triggered
                </span>
                <span className="text-sm text-slate-600 dark:text-slate-400">
                  <RelativeDate iso={execution.startedAt} />
                </span>
              </div>
            </div>

            {/* Trigger Event Data */}
            <div>
              <h3 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-200">
                Trigger Event Data
              </h3>
              <div className="rounded-md border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-800/50">
                {Object.keys(execution.triggerEventData).length > 0 ? (
                  <dl className="space-y-1">
                    {Object.entries(execution.triggerEventData).map(([key, value]) => (
                      <div key={key} className="flex items-start gap-2">
                        <dt className="font-mono text-xs text-slate-500 dark:text-slate-400">
                          {key}:
                        </dt>
                        <dd className="font-mono text-xs text-slate-700 dark:text-slate-300">
                          {typeof value === "object" ? JSON.stringify(value) : String(value)}
                        </dd>
                      </div>
                    ))}
                  </dl>
                ) : (
                  <p className="text-xs text-slate-500 italic dark:text-slate-400">No event data</p>
                )}
              </div>
            </div>

            {/* Error Message */}
            {execution.errorMessage && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-800 dark:bg-red-900/20">
                <p className="text-sm text-red-700 dark:text-red-400">{execution.errorMessage}</p>
              </div>
            )}

            {/* Action Executions */}
            <div>
              <h3 className="mb-3 text-sm font-semibold text-slate-800 dark:text-slate-200">
                Actions ({execution.actionExecutions.length})
              </h3>
              <div className="space-y-3">
                {execution.actionExecutions.length === 0 ? (
                  <p className="text-sm text-slate-500 italic dark:text-slate-400">
                    No action executions
                  </p>
                ) : (
                  execution.actionExecutions.map((action) => {
                    const statusConfig = ACTION_STATUS_CONFIG[action.status] ?? {
                      label: action.status,
                      variant: "neutral" as const,
                    };
                    return (
                      <div
                        key={action.id}
                        className="rounded-md border border-slate-200 p-3 dark:border-slate-700"
                      >
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                            {formatActionType(action.actionType)}
                          </span>
                          <Badge variant={statusConfig.variant}>{statusConfig.label}</Badge>
                        </div>
                        <div className="mt-2 space-y-1 text-xs text-slate-500 dark:text-slate-400">
                          {action.scheduledFor && (
                            <p>
                              Scheduled: <RelativeDate iso={action.scheduledFor} />
                            </p>
                          )}
                          {action.executedAt && (
                            <p>
                              Executed: <RelativeDate iso={action.executedAt} />
                            </p>
                          )}
                          {action.resultData && Object.keys(action.resultData).length > 0 && (
                            <div className="mt-1 rounded bg-slate-50 p-2 dark:bg-slate-800/50">
                              <p className="font-mono">{JSON.stringify(action.resultData)}</p>
                            </div>
                          )}
                          {action.errorMessage && (
                            <div className="mt-1">
                              <button
                                type="button"
                                onClick={() => toggleError(action.id)}
                                className="inline-flex items-center gap-1 text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                              >
                                <ChevronDown
                                  className={`size-3 transition-transform ${expandedErrors.has(action.id) ? "rotate-180" : ""}`}
                                />
                                Error details
                              </button>
                              {expandedErrors.has(action.id) && (
                                <p className="mt-1 rounded bg-red-50 p-2 text-red-700 dark:bg-red-900/20 dark:text-red-400">
                                  {action.errorMessage}
                                </p>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
