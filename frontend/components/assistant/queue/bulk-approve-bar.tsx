"use client";

import { useState, useCallback } from "react";
import { Check, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AI_QUEUE_STRINGS } from "@/lib/constants/ai-queue-strings";
import { bulkApproveInvocations } from "@/lib/api/assistant-specialists";
import { cn } from "@/lib/utils";

const MAX_BULK = 25;

export interface BulkApproveBarProps {
  selectedIds: string[];
  /** Map of id -> specialistId for validating same-specialist constraint */
  selectedSpecialists: Record<string, string>;
  onComplete: () => void;
  onClear: () => void;
}

export function BulkApproveBar({
  selectedIds,
  selectedSpecialists,
  onComplete,
  onClear,
}: BulkApproveBarProps) {
  const [inFlight, setInFlight] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const specialistIds = new Set(Object.values(selectedSpecialists));
  const sameSpecialist = specialistIds.size <= 1;
  const withinCap = selectedIds.length <= MAX_BULK;
  const canApprove = selectedIds.length > 0 && sameSpecialist && withinCap && !inFlight;

  const handleBulkApprove = useCallback(async () => {
    if (!canApprove) return;
    setInFlight(true);
    setError(null);
    try {
      await bulkApproveInvocations(selectedIds);
      onComplete();
    } catch {
      setError("Bulk approve failed. Please try again.");
    } finally {
      setInFlight(false);
    }
  }, [canApprove, selectedIds, onComplete]);

  if (selectedIds.length === 0) return null;

  return (
    <div
      className={cn(
        "fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-lg border bg-white px-4 py-3 shadow-lg dark:bg-slate-900",
        "flex items-center gap-3"
      )}
      data-testid="bulk-approve-bar"
    >
      {!sameSpecialist && (
        <span className="flex items-center gap-1 text-sm text-amber-600">
          <AlertCircle className="size-4" />
          {AI_QUEUE_STRINGS.bulkApprove.sameSpecialistError}
        </span>
      )}
      {!withinCap && (
        <span className="flex items-center gap-1 text-sm text-amber-600">
          <AlertCircle className="size-4" />
          {AI_QUEUE_STRINGS.bulkApprove.capExceeded}
        </span>
      )}
      {error && <span className="text-sm text-red-600">{error}</span>}

      <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
        {selectedIds.length} selected
      </span>

      <Button size="sm" disabled={!canApprove} onClick={handleBulkApprove}>
        <Check className="mr-1 size-4" />
        {AI_QUEUE_STRINGS.bulkApprove.cta(selectedIds.length)}
      </Button>

      <Button variant="ghost" size="sm" onClick={onClear}>
        Clear
      </Button>
    </div>
  );
}
