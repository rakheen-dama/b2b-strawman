"use client";

import { useState } from "react";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  cancelBillingRunAction,
  batchApproveAction,
} from "@/app/(app)/org/[slug]/invoices/billing-runs/[id]/actions";
import { AlertTriangle, Ban, CheckCircle } from "lucide-react";
import type { BillingRunStatus } from "@/lib/api/billing-runs";

interface BillingRunDetailActionsProps {
  slug: string;
  billingRunId: string;
  status: BillingRunStatus;
}

export function BillingRunDetailActions({
  slug,
  billingRunId,
  status,
}: BillingRunDetailActionsProps) {
  const [isCancelling, setIsCancelling] = useState(false);
  const [isApproving, setIsApproving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canCancel =
    status === "PREVIEW" || status === "IN_PROGRESS" || status === "COMPLETED";
  const canApprove = status === "COMPLETED";

  async function handleCancel() {
    setError(null);
    setIsCancelling(true);
    try {
      const result = await cancelBillingRunAction(slug, billingRunId);
      if (!result.success) {
        setError(result.error ?? "Failed to cancel billing run.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsCancelling(false);
    }
  }

  async function handleApprove() {
    setError(null);
    setIsApproving(true);
    try {
      const result = await batchApproveAction(slug, billingRunId);
      if (!result.success) {
        setError(result.error ?? "Failed to approve invoices.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsApproving(false);
    }
  }

  if (!canCancel && !canApprove) {
    return null;
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      {error && (
        <p role="alert" className="w-full text-sm text-destructive">{error}</p>
      )}

      {canApprove && (
        <Button
          variant="default"
          onClick={handleApprove}
          disabled={isApproving}
        >
          <CheckCircle className="mr-1.5 size-4" />
          {isApproving ? "Approving..." : "Approve All Generated"}
        </Button>
      )}

      {canCancel && (
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button variant="destructive">
              <Ban className="mr-1.5 size-4" />
              Cancel Run
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent className="border-t-4 border-t-red-500">
            <AlertDialogHeader>
              <div className="flex justify-center">
                <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
                  <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
                </div>
              </div>
              <AlertDialogTitle className="text-center">
                Cancel Billing Run
              </AlertDialogTitle>
              <AlertDialogDescription className="text-center">
                Are you sure you want to cancel this billing run? This action
                cannot be undone. Any generated invoices will remain but the run
                will be marked as cancelled.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel variant="plain" disabled={isCancelling}>
                Keep Run
              </AlertDialogCancel>
              <Button
                variant="destructive"
                onClick={handleCancel}
                disabled={isCancelling}
              >
                {isCancelling ? "Cancelling..." : "Cancel Run"}
              </Button>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      )}
    </div>
  );
}
