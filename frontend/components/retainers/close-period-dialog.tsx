"use client";

import { useState } from "react";
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
import { formatLocalDate, formatCurrency } from "@/lib/format";
import { closePeriodAction } from "@/app/(app)/org/[slug]/retainers/[id]/actions";
import type { PeriodSummary, RetainerResponse } from "@/lib/api/retainers";

interface ClosePeriodDialogProps {
  slug: string;
  retainerId: string;
  period: PeriodSummary;
  retainer: RetainerResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ClosePeriodDialog({
  slug,
  retainerId,
  period,
  retainer,
  open,
  onOpenChange,
}: ClosePeriodDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const baseFeeAmount = retainer.periodFee ?? 0;
  const hasOverage = period.overageHours > 0;

  // Preview estimate — backend recalculates at close time
  const remainingHours = period.remainingHours;
  const rolloverOut =
    retainer.rolloverPolicy === "FORFEIT" || !retainer.rolloverPolicy
      ? 0
      : retainer.rolloverPolicy === "CARRY_CAPPED"
        ? Math.min(remainingHours, retainer.rolloverCapHours ?? 0)
        : remainingHours; // CARRY_FORWARD

  async function handleClose() {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await closePeriodAction(slug, retainerId);
      if (result.success) {
        onOpenChange(false);
      } else {
        setError(result.error ?? "Failed to close period.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AlertDialog
      open={open}
      onOpenChange={(o) => {
        if (!isSubmitting) onOpenChange(o);
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Close Period</AlertDialogTitle>
          <AlertDialogDescription>
            Close the current period and generate an invoice for this retainer.
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-4 py-2">
          {/* Period summary */}
          <div className="rounded-md border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900">
            <dl className="space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500 dark:text-slate-400">Period:</dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {formatLocalDate(period.periodStart)} &ndash;{" "}
                  {formatLocalDate(period.periodEnd)}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500 dark:text-slate-400">
                  Allocated Hours:
                </dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {period.allocatedHours.toFixed(1)}h
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500 dark:text-slate-400">
                  Consumed Hours:
                </dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {period.consumedHours.toFixed(1)}h
                </dd>
              </div>
            </dl>
          </div>

          {/* Overage warning */}
          {hasOverage && (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
              {period.overageHours.toFixed(1)} overage hours recorded.
              Overage charges will apply.
            </div>
          )}

          {/* Rollover preview */}
          {rolloverOut > 0 && remainingHours > 0 && (
            <div className="text-sm text-slate-600 dark:text-slate-400">
              {rolloverOut.toFixed(1)} hours will roll over to the next period.
            </div>
          )}

          {/* Invoice preview */}
          <div className="space-y-2">
            <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              Invoice Preview
            </h4>
            <div className="rounded-md border border-slate-200 p-3 dark:border-slate-800">
              <div className="space-y-1 text-sm">
                <div className="flex justify-between">
                  <span className="text-slate-600 dark:text-slate-400">
                    Base fee
                  </span>
                  <span className="font-medium text-slate-900 dark:text-slate-100">
                    {formatCurrency(baseFeeAmount, "USD")}
                  </span>
                </div>
                {hasOverage && (
                  <div className="flex justify-between">
                    <span className="text-slate-600 dark:text-slate-400">
                      Overage ({period.overageHours.toFixed(1)}h)
                    </span>
                    <span className="font-medium text-amber-600 dark:text-amber-400">
                      Calculated at close
                    </span>
                  </div>
                )}
                <div className="border-t border-slate-100 pt-1 dark:border-slate-800">
                  <div className="flex justify-between font-medium">
                    <span className="text-slate-900 dark:text-slate-100">
                      Estimated Total
                    </span>
                    <span className="text-slate-900 dark:text-slate-100">
                      {hasOverage
                        ? `${formatCurrency(baseFeeAmount, "USD")}+`
                        : formatCurrency(baseFeeAmount, "USD")}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel disabled={isSubmitting}>Cancel</AlertDialogCancel>
          <AlertDialogAction onClick={(e) => { e.preventDefault(); handleClose(); }} disabled={isSubmitting}>
            {isSubmitting ? "Closing..." : "Close Period & Generate Invoice"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
