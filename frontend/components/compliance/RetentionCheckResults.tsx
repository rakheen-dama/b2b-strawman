"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { CheckCircle2, AlertTriangle, ChevronDown, ChevronUp, Loader2 } from "lucide-react";
import type { RetentionCheckResult } from "@/lib/types";

interface RetentionCheckResultsProps {
  result: RetentionCheckResult;
  onPurge: (recordType: string, recordIds: string[]) => Promise<void>;
}

export function RetentionCheckResults({ result, onPurge }: RetentionCheckResultsProps) {
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [purgingKeys, setPurgingKeys] = useState<Set<string>>(new Set());

  function toggleExpanded(key: string) {
    setExpandedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  async function handlePurge(key: string, recordType: string, recordIds: string[]) {
    if (
      !window.confirm(
        `Purge ${recordIds.length} ${recordType} records? This cannot be undone.`,
      )
    )
      return;
    setPurgingKeys((prev) => new Set(prev).add(key));
    try {
      await onPurge(recordType, recordIds);
    } finally {
      setPurgingKeys((prev) => {
        const next = new Set(prev);
        next.delete(key);
        return next;
      });
    }
  }

  if (result.totalFlagged === 0) {
    return (
      <div className="mt-4 rounded-lg border border-green-200 bg-green-50 p-4 dark:border-green-900 dark:bg-green-950">
        <div className="flex items-center gap-2">
          <CheckCircle2 className="size-5 text-green-600 dark:text-green-400" />
          <p className="font-medium text-green-800 dark:text-green-200">
            All Clear
          </p>
        </div>
        <p className="mt-1 text-sm text-green-700 dark:text-green-300">
          No records match the current retention policies. Checked at{" "}
          {new Date(result.checkedAt).toLocaleString()}.
        </p>
      </div>
    );
  }

  const entries = Object.entries(result.flagged);

  return (
    <div className="mt-4 space-y-3">
      <div className="flex items-center gap-2">
        <AlertTriangle className="size-5 text-amber-600 dark:text-amber-400" />
        <p className="font-medium text-slate-900 dark:text-slate-100">
          {result.totalFlagged} record{result.totalFlagged !== 1 ? "s" : ""} flagged
        </p>
        <span className="text-sm text-slate-500 dark:text-slate-400">
          Checked at {new Date(result.checkedAt).toLocaleString()}
        </span>
      </div>

      {entries.map(([key, flagged]) => {
        const isExpanded = expandedKeys.has(key);
        const isPurging = purgingKeys.has(key);
        const visibleIds = isExpanded ? flagged.recordIds : flagged.recordIds.slice(0, 5);

        return (
          <div
            key={key}
            className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-slate-900 dark:text-slate-100">
                  {flagged.recordType} / {flagged.triggerEvent}
                </p>
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  {flagged.count} record{flagged.count !== 1 ? "s" : ""} &middot; Action:{" "}
                  {flagged.action}
                </p>
              </div>
              <Button
                size="sm"
                variant="destructive"
                disabled={isPurging}
                onClick={() => handlePurge(key, flagged.recordType, flagged.recordIds)}
              >
                {isPurging && <Loader2 className="mr-1.5 size-4 animate-spin" />}
                Purge
              </Button>
            </div>

            <div className="mt-2 space-y-1">
              {visibleIds.map((id) => (
                <p key={id} className="font-mono text-xs text-slate-500 dark:text-slate-400">
                  {id}
                </p>
              ))}
              {flagged.recordIds.length > 5 && (
                <button
                  type="button"
                  onClick={() => toggleExpanded(key)}
                  className="mt-1 inline-flex items-center gap-1 text-xs font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                >
                  {isExpanded ? (
                    <>
                      <ChevronUp className="size-3" />
                      Show fewer
                    </>
                  ) : (
                    <>
                      <ChevronDown className="size-3" />
                      Show all {flagged.recordIds.length}
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
