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
import { rejectAccessRequest } from "@/app/(app)/platform-admin/access-requests/actions";

interface RejectDialogProps {
  requestId: string;
  orgName: string;
  email: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function RejectDialog({
  requestId,
  orgName,
  email,
  open,
  onOpenChange,
  onSuccess,
}: RejectDialogProps) {
  const [isRejecting, setIsRejecting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleReject() {
    setError(null);
    setIsRejecting(true);

    try {
      const result = await rejectAccessRequest(requestId);
      if (result.success) {
        onOpenChange(false);
        onSuccess();
      } else {
        setError(result.error ?? "Failed to reject request.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsRejecting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <AlertDialogTitle>Reject Access Request</AlertDialogTitle>
          <AlertDialogDescription>
            Reject access request from{" "}
            <span className="text-foreground font-semibold">{email}</span> for{" "}
            <span className="text-foreground font-semibold">{orgName}</span>? This action cannot be
            undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isRejecting}>
            Cancel
          </AlertDialogCancel>
          <Button variant="destructive" onClick={handleReject} disabled={isRejecting}>
            {isRejecting ? "Rejecting..." : "Reject"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
