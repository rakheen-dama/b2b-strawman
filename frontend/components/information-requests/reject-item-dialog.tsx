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
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

interface RejectItemDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  itemName: string;
  onConfirm: (reason: string) => Promise<void>;
}

export function RejectItemDialog({
  open,
  onOpenChange,
  itemName,
  onConfirm,
}: RejectItemDialogProps) {
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reason, setReason] = useState("");

  async function handleConfirm() {
    setError(null);
    setIsPending(true);
    try {
      await onConfirm(reason);
      onOpenChange(false);
      setReason("");
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsPending(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (!newOpen) {
      setError(null);
      setReason("");
    }
    onOpenChange(newOpen);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Reject Item</AlertDialogTitle>
          <AlertDialogDescription>
            Provide a reason for rejecting &quot;{itemName}&quot;. The client will be notified and
            can resubmit.
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-2">
          <label className="text-sm text-slate-600 dark:text-slate-400">Rejection reason</label>
          <Textarea
            placeholder="Explain why this item is being rejected..."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="resize-none"
            rows={3}
            data-testid="reject-reason-input"
          />
        </div>

        {error && <p className="text-destructive text-sm">{error}</p>}

        <AlertDialogFooter>
          <AlertDialogCancel disabled={isPending}>Cancel</AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={handleConfirm}
            disabled={isPending || !reason.trim()}
          >
            {isPending ? "Rejecting..." : "Reject"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
