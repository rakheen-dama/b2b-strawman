"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
import {
  approveDisbursementAction,
  rejectDisbursementAction,
} from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import {
  approvalNotesSchema,
  rejectionNotesSchema,
  type ApprovalNotesFormData,
  type RejectionNotesFormData,
} from "@/lib/schemas/legal";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

interface DisbursementApprovalPanelProps {
  slug: string;
  disbursement: DisbursementResponse;
  canApprove: boolean;
  onApproved?: (updated: DisbursementResponse) => void;
  onRejected?: (updated: DisbursementResponse) => void;
}

export function DisbursementApprovalPanel({
  slug,
  disbursement,
  canApprove,
  onApproved,
  onRejected,
}: DisbursementApprovalPanelProps) {
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);

  if (!canApprove || disbursement.approvalStatus !== "PENDING_APPROVAL") {
    return null;
  }

  return (
    <Card data-testid="disbursement-approval-panel">
      <CardHeader>
        <CardTitle className="text-sm font-medium">Approval Required</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <p className="text-slate-600 dark:text-slate-400">
          This disbursement is pending approval. Review the amount and supplier details before
          approving, or reject with a reason.
        </p>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setApproveDialogOpen(true)}
            data-testid="disbursement-approve-button"
          >
            <CheckCircle2 className="mr-1.5 size-3.5" />
            Approve
          </Button>
          <Button
            type="button"
            variant="destructive"
            size="sm"
            onClick={() => setRejectDialogOpen(true)}
            data-testid="disbursement-reject-button"
          >
            <XCircle className="mr-1.5 size-3.5" />
            Reject
          </Button>
        </div>
      </CardContent>

      <ApproveDialog
        slug={slug}
        disbursementId={disbursement.id}
        open={approveDialogOpen}
        onOpenChange={setApproveDialogOpen}
        onApproved={onApproved}
      />

      <RejectDialog
        slug={slug}
        disbursementId={disbursement.id}
        open={rejectDialogOpen}
        onOpenChange={setRejectDialogOpen}
        onRejected={onRejected}
      />
    </Card>
  );
}

interface ApproveDialogProps {
  slug: string;
  disbursementId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onApproved?: (updated: DisbursementResponse) => void;
}

function ApproveDialog({
  slug,
  disbursementId,
  open,
  onOpenChange,
  onApproved,
}: ApproveDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<ApprovalNotesFormData>({
    resolver: zodResolver(approvalNotesSchema),
    defaultValues: { notes: "" },
  });

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset({ notes: "" });
      setError(null);
    }
  }

  async function handleSubmit(values: ApprovalNotesFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await approveDisbursementAction(slug, disbursementId, values.notes);
      if (result.success && result.data) {
        onApproved?.(result.data);
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Failed to approve disbursement");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md" data-testid="disbursement-approve-dialog">
        <DialogHeader>
          <DialogTitle>Approve Disbursement</DialogTitle>
          <DialogDescription>
            Optionally add notes explaining the approval decision.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="notes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Notes <span className="font-normal text-slate-500">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Approval notes"
                      maxLength={2000}
                      data-testid="disbursement-approve-notes-input"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && (
              <p role="alert" className="text-sm text-red-600">
                {error}
              </p>
            )}

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
                disabled={isSubmitting}
                data-testid="disbursement-confirm-approve-button"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Approving...
                  </>
                ) : (
                  "Approve Disbursement"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

interface RejectDialogProps {
  slug: string;
  disbursementId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onRejected?: (updated: DisbursementResponse) => void;
}

function RejectDialog({ slug, disbursementId, open, onOpenChange, onRejected }: RejectDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<RejectionNotesFormData>({
    resolver: zodResolver(rejectionNotesSchema),
    defaultValues: { notes: "" },
  });

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset({ notes: "" });
      setError(null);
    }
  }

  async function handleSubmit(values: RejectionNotesFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await rejectDisbursementAction(slug, disbursementId, values.notes);
      if (result.success && result.data) {
        onRejected?.(result.data);
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Failed to reject disbursement");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md" data-testid="disbursement-reject-dialog">
        <DialogHeader>
          <DialogTitle>Reject Disbursement</DialogTitle>
          <DialogDescription>
            Provide a reason so the submitter knows what to correct.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="notes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reason</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Reason for rejection"
                      maxLength={2000}
                      data-testid="disbursement-reject-notes-input"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && (
              <p role="alert" className="text-sm text-red-600">
                {error}
              </p>
            )}

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
                data-testid="disbursement-confirm-reject-button"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Rejecting...
                  </>
                ) : (
                  "Reject Disbursement"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
