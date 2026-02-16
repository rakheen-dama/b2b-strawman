"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ChevronDown, ChevronRight } from "lucide-react";
import { ChecklistItemRow } from "./ChecklistItemRow";
import type {
  ChecklistInstanceResponse,
  ChecklistInstanceItemResponse,
  ChecklistInstanceStatus,
} from "@/lib/types";

const INSTANCE_STATUS_BADGE: Record<
  ChecklistInstanceStatus,
  { label: string; variant: "neutral" | "success" | "destructive" }
> = {
  IN_PROGRESS: { label: "In Progress", variant: "neutral" },
  COMPLETED: { label: "Completed", variant: "success" },
  CANCELLED: { label: "Cancelled", variant: "destructive" },
};

interface ChecklistProgressProps {
  instance: ChecklistInstanceResponse;
  items: ChecklistInstanceItemResponse[];
  canManage: boolean;
  slug: string;
  customerId: string;
}

export function ChecklistProgress({
  instance,
  items,
  canManage,
  slug,
  customerId,
}: ChecklistProgressProps) {
  const [expanded, setExpanded] = useState(instance.status === "IN_PROGRESS");

  const badge = INSTANCE_STATUS_BADGE[instance.status];
  const progressPct =
    instance.requiredCount > 0
      ? Math.round((instance.requiredCompletedCount / instance.requiredCount) * 100)
      : 100;

  const sortedItems = [...items].sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      {/* Header */}
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center justify-between gap-4 px-4 py-3 text-left"
      >
        <div className="flex min-w-0 flex-1 items-center gap-3">
          {expanded ? (
            <ChevronDown className="size-4 shrink-0 text-slate-400" />
          ) : (
            <ChevronRight className="size-4 shrink-0 text-slate-400" />
          )}
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <p className="font-medium text-slate-950 dark:text-slate-50">
                {instance.templateName}
              </p>
              <Badge variant={badge.variant}>{badge.label}</Badge>
            </div>
            <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
              {instance.requiredCompletedCount} of {instance.requiredCount} required items completed
            </p>
          </div>
        </div>

        {/* Progress bar */}
        <div className="flex w-32 shrink-0 items-center gap-2">
          <div
            role="progressbar"
            aria-valuenow={progressPct}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`${instance.requiredCompletedCount} of ${instance.requiredCount} required items completed`}
            className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800"
          >
            <div
              className="h-full rounded-full bg-teal-500 transition-all"
              style={{ width: `${progressPct}%` }}
            />
          </div>
          <span className="text-xs font-medium text-slate-600 dark:text-slate-400">
            {progressPct}%
          </span>
        </div>
      </button>

      {/* Items list */}
      {expanded && (
        <div className="space-y-2 border-t border-slate-200 px-4 py-3 dark:border-slate-800">
          {sortedItems.map((item) => (
            <ChecklistItemRow
              key={item.id}
              item={item}
              canManage={canManage}
              slug={slug}
              customerId={customerId}
            />
          ))}
        </div>
      )}
    </div>
  );
}
