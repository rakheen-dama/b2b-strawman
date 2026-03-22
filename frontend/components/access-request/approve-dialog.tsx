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
import { approveAccessRequest } from "@/app/(app)/platform-admin/access-requests/actions";

interface ApproveDialogProps {
  requestId: string;
  orgName: string;
  email: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function ApproveDialog({
  requestId,
  orgName,
  email,
  open,
  onOpenChange,
  onSuccess,
}: ApproveDialogProps) {
  const [isApproving, setIsApproving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleApprove() {
    setError(null);
    setIsApproving(true);

    try {
      const result = await approveAccessRequest(requestId);
      if (result.success) {
        onOpenChange(false);
        onSuccess();
      } else {
        setError(result.error ?? "Failed to approve request.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsApproving(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Approve Access Request</AlertDialogTitle>
          <AlertDialogDescription>
            Approve access request for{" "}
            <span className="text-foreground font-semibold">{orgName}</span>? This will create a
            Keycloak organization, provision a tenant schema, and send an invitation to{" "}
            <span className="text-foreground font-semibold">{email}</span>.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isApproving}>
            Cancel
          </AlertDialogCancel>
          <Button variant="accent" onClick={handleApprove} disabled={isApproving} data-testid="confirm-approve-btn">
            {isApproving ? "Approving..." : "Approve"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
