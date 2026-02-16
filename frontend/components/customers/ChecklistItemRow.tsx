"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Check, RotateCcw, SkipForward, AlertTriangle, FileText } from "lucide-react";
import { completeChecklistItem, skipChecklistItem, reopenChecklistItem } from "@/lib/actions/checklists";
import type { ChecklistInstanceItemResponse, ChecklistItemStatus } from "@/lib/types";

const STATUS_BADGE: Record<
  ChecklistItemStatus,
  { label: string; variant: "neutral" | "warning" | "success" }
> = {
  PENDING: { label: "Pending", variant: "neutral" },
  BLOCKED: { label: "Blocked", variant: "warning" },
  COMPLETED: { label: "Completed", variant: "success" },
  SKIPPED: { label: "Skipped", variant: "neutral" },
};

interface ChecklistItemRowProps {
  item: ChecklistInstanceItemResponse;
  canManage: boolean;
  slug: string;
  customerId: string;
}

export function ChecklistItemRow({ item, canManage, slug, customerId }: ChecklistItemRowProps) {
  const [loading, setLoading] = useState(false);
  const badge = STATUS_BADGE[item.status];

  async function handleComplete() {
    setLoading(true);
    await completeChecklistItem(slug, customerId, item.id);
    setLoading(false);
  }

  async function handleSkip() {
    setLoading(true);
    await skipChecklistItem(slug, customerId, item.id, "Skipped by admin");
    setLoading(false);
  }

  async function handleReopen() {
    setLoading(true);
    await reopenChecklistItem(slug, customerId, item.id);
    setLoading(false);
  }

  const showComplete = canManage && item.status === "PENDING";
  const showSkip = canManage && item.status === "PENDING" && !item.required;
  const showReopen = canManage && (item.status === "COMPLETED" || item.status === "SKIPPED");

  return (
    <div className="flex items-center justify-between gap-4 rounded-lg border border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-950">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
            {item.name}
          </p>
          <Badge variant={badge.variant}>{badge.label}</Badge>
          {item.required && (
            <span className="text-xs text-slate-500 dark:text-slate-400">Required</span>
          )}
        </div>
        {item.description && (
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
            {item.description}
          </p>
        )}
        {item.status === "BLOCKED" && item.dependsOnItemId && (
          <p className="mt-1 flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
            <AlertTriangle className="size-3" />
            Blocked by a dependency
          </p>
        )}
        {item.documentId && (
          <p className="mt-1 flex items-center gap-1 text-xs text-teal-600 dark:text-teal-400">
            <FileText className="size-3" />
            Document attached
          </p>
        )}
        {item.notes && (
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Note: {item.notes}
          </p>
        )}
      </div>

      <div className="flex shrink-0 gap-1.5">
        {showComplete && (
          <Button
            variant="outline"
            size="sm"
            onClick={handleComplete}
            disabled={loading}
          >
            <Check className="mr-1 size-3.5" />
            Complete
          </Button>
        )}
        {showSkip && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleSkip}
            disabled={loading}
          >
            <SkipForward className="mr-1 size-3.5" />
            Skip
          </Button>
        )}
        {showReopen && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleReopen}
            disabled={loading}
          >
            <RotateCcw className="mr-1 size-3.5" />
            Reopen
          </Button>
        )}
      </div>
    </div>
  );
}
