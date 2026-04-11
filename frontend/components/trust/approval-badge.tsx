"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { CheckCircle2, XCircle, Loader2 } from "lucide-react";
import {
  approveTransaction,
  rejectTransaction,
} from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { rejectionReasonSchema, type RejectionReasonFormData } from "@/lib/schemas/trust";

interface ApprovalBadgeProps {
  transactionId: string;
  status: string;
}

export function ApprovalBadge({ transactionId, status }: ApprovalBadgeProps) {
  const [isApproving, setIsApproving] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (status !== "AWAITING_APPROVAL") {
    return null;
  }

  async function handleApprove() {
    setError(null);
    setIsApproving(true);
    try {
      const result = await approveTransaction(transactionId);
      if (!result.success) {
        setError(result.error ?? "Failed to approve");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsApproving(false);
    }
  }

  return (
    <div className="flex items-center gap-2">
      <Button
        variant="outline"
        size="sm"
        onClick={handleApprove}
        disabled={isApproving}
        data-testid="approve-button"
      >
        {isApproving ? (
          <Loader2 className="mr-1 size-3 animate-spin" />
        ) : (
          <CheckCircle2 className="mr-1 size-3" />
        )}
        Approve
      </Button>
      <Button
        variant="destructive"
        size="sm"
        onClick={() => setRejectDialogOpen(true)}
        disabled={isApproving}
        data-testid="reject-button"
      >
        <XCircle className="mr-1 size-3" />
        Reject
      </Button>
      {error && <span className="text-destructive text-xs">{error}</span>}
      <RejectDialog
        transactionId={transactionId}
        open={rejectDialogOpen}
        onOpenChange={setRejectDialogOpen}
      />
    </div>
  );
}

// ── Reject Dialog ─────────────────────────────────────────────────

function RejectDialog({
  transactionId,
  open,
  onOpenChange,
}: {
  transactionId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<RejectionReasonFormData>({
    resolver: zodResolver(rejectionReasonSchema),
    defaultValues: {
      reason: "",
    },
  });

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset();
      setError(null);
    }
  }

  async function handleSubmit(data: RejectionReasonFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await rejectTransaction(transactionId, data.reason);
      if (result.success) {
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Failed to reject transaction");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Reject Transaction</DialogTitle>
          <DialogDescription>
            Please provide a reason for rejecting this transaction.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="reason"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reason</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Reason for rejection"
                      maxLength={500}
                      data-testid="rejection-reason-input"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && <p className="text-destructive text-sm">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="destructive"
                disabled={isSubmitting}
                data-testid="confirm-reject-button"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Rejecting...
                  </>
                ) : (
                  "Reject Transaction"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
