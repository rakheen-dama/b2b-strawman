"use client";

import { useState, type ReactNode } from "react";
import { toast } from "sonner";
import { DisbursementWriteOffFormDialog } from "@/components/legal/disbursement-write-off-dialog";
import { restoreDisbursementAction } from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import { useCapabilities } from "@/lib/capabilities";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

interface UseDisbursementWriteOffOptions {
  slug: string;
  /**
   * Called after a successful write-off or undo with the changed row's matter id — refetch the
   * list and refresh / evict that matter's unbilled strip here.
   */
  onChanged: (matterId: string) => void;
}

interface UseDisbursementWriteOff {
  canWriteOff: boolean;
  /** Pass to `DisbursementListView`; undefined without WRITE_OFF_DISBURSEMENTS. */
  onWriteOff: ((disbursement: DisbursementResponse) => void) | undefined;
  /** Pass to `DisbursementListView`; undefined without WRITE_OFF_DISBURSEMENTS. */
  onRestore: ((disbursement: DisbursementResponse) => void) | undefined;
  /** The row-menu write-off dialog — render it OUTSIDE the list's `<tr>` (clicks bubble to the row). */
  dialog: ReactNode;
}

/**
 * Row-menu write-off / undo wiring shared by the firm disbursement list and the matter tab.
 * Write-off / undo need WRITE_OFF_DISBURSEMENTS (backend @RequiresCapability) — not canManage.
 */
export function useDisbursementWriteOff({
  slug,
  onChanged,
}: UseDisbursementWriteOffOptions): UseDisbursementWriteOff {
  const { hasCapability } = useCapabilities();
  const canWriteOff = hasCapability("WRITE_OFF_DISBURSEMENTS");
  // The dialog stays mounted once opened and keeps the last row while it closes, so Radix can
  // run its exit + focus return instead of being torn down mid-transition.
  const [target, setTarget] = useState<{ disbursement: DisbursementResponse; open: boolean }>();

  async function handleRestore(disbursement: DisbursementResponse) {
    try {
      const result = await restoreDisbursementAction(slug, disbursement.id);
      if (result.success) {
        toast.success("Write-off undone");
        onChanged(disbursement.matterId);
      } else {
        toast.error(result.error ?? "Failed to undo write-off");
      }
    } catch {
      toast.error("An unexpected error occurred while undoing the write-off");
    }
  }

  const dialog = target ? (
    <DisbursementWriteOffFormDialog
      slug={slug}
      disbursement={target.disbursement}
      open={target.open}
      onOpenChange={(open) => setTarget((current) => current && { ...current, open })}
      onWrittenOff={() => {
        toast.success("Disbursement written off");
        onChanged(target.disbursement.matterId);
      }}
    />
  ) : null;

  return {
    canWriteOff,
    onWriteOff: canWriteOff ? (disbursement) => setTarget({ disbursement, open: true }) : undefined,
    onRestore: canWriteOff ? handleRestore : undefined,
    dialog,
  };
}
