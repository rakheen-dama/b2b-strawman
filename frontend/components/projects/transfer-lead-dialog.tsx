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
import { transferLead } from "@/app/(app)/org/[slug]/projects/[id]/member-actions";

interface TransferLeadDialogProps {
  slug: string;
  projectId: string;
  targetMemberId: string;
  targetMemberName: string;
  children: React.ReactNode;
}

export function TransferLeadDialog({
  slug,
  projectId,
  targetMemberId,
  targetMemberName,
  children,
}: TransferLeadDialogProps) {
  const [open, setOpen] = useState(false);
  const [isTransferring, setIsTransferring] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleTransfer() {
    setError(null);
    setIsTransferring(true);

    try {
      const result = await transferLead(slug, projectId, targetMemberId);
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to transfer lead role.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsTransferring(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (isTransferring) return;
    if (newOpen) {
      setError(null);
    }
    setOpen(newOpen);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Transfer Lead Role</AlertDialogTitle>
          <AlertDialogDescription>
            Transfer the lead role to{" "}
            <span className="text-foreground font-semibold">{targetMemberName}</span>? You will
            become a regular member. This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-destructive text-sm">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isTransferring}>
            Cancel
          </AlertDialogCancel>
          <Button variant="accent" onClick={handleTransfer} disabled={isTransferring}>
            {isTransferring ? "Transferring..." : "Transfer"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
