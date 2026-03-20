"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { FilingStatusDialog } from "@/components/deadlines/FilingStatusDialog";
import { updateFilingStatus } from "@/app/(app)/org/[slug]/deadlines/actions";
import type { CalculatedDeadline } from "@/lib/types";

function derivePeriodKey(dueDate: string): string {
  return dueDate.substring(0, 4);
}

interface BatchFilingActionsProps {
  selectedIds: Set<string>;
  deadlines: CalculatedDeadline[];
  slug: string;
  onClearSelection: () => void;
  onFilingSuccess: () => void;
}

export function BatchFilingActions({
  selectedIds,
  deadlines,
  slug,
  onClearSelection,
  onFilingSuccess,
}: BatchFilingActionsProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isMarkingNA, setIsMarkingNA] = useState(false);

  if (selectedIds.size === 0) return null;

  const selectedDeadlines = deadlines.filter((d) =>
    selectedIds.has(`${d.customerId}__${d.deadlineTypeSlug}__${d.dueDate}`)
  );

  async function handleMarkNA() {
    setIsMarkingNA(true);
    try {
      const items = selectedDeadlines.map((deadline) => ({
        customerId: deadline.customerId,
        deadlineTypeSlug: deadline.deadlineTypeSlug,
        periodKey: derivePeriodKey(deadline.dueDate),
        status: "not_applicable" as const,
      }));
      const result = await updateFilingStatus(slug, items);
      if (result.success) {
        onFilingSuccess();
        onClearSelection();
      }
    } finally {
      setIsMarkingNA(false);
    }
  }

  function handleFilingSuccess() {
    onFilingSuccess();
    onClearSelection();
  }

  return (
    <>
      <div className="sticky bottom-0 z-10 border-t border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-950">
        <div className="flex items-center gap-3">
          <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
            {selectedIds.size} selected
          </span>
          <Button size="sm" onClick={() => setDialogOpen(true)}>
            Mark as Filed
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={handleMarkNA}
            disabled={isMarkingNA}
          >
            {isMarkingNA ? "Updating..." : "Mark as N/A"}
          </Button>
          <Button size="sm" variant="ghost" onClick={onClearSelection}>
            Clear
          </Button>
        </div>
      </div>
      <FilingStatusDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        deadlines={selectedDeadlines}
        slug={slug}
        onSuccess={handleFilingSuccess}
      />
    </>
  );
}
