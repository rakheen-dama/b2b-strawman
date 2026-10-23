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
import { Button } from "@b2mash/ui/button";
import { transferLead } from "@/app/(app)/org/[slug]/matters/[id]/member-actions";

interface TransferLeadDialogProps {
  slug: string;
  projectId: string;
  targetMemberId: string;
  targetMemberName: string;
  /**
   * The caller is the current lead (copy: "You will become a regular member"). Owner/Admin callers
   * who are not the lead pass false, and the copy names the current lead instead (ADR-346).
   */
  actorIsLead?: boolean;
  /** The caller is transferring the lead to themselves (an Owner/Admin on the matter as a member). */
  targetIsSelf?: boolean;
  /** Name of the lead who will be demoted; shown when the caller is not the lead. */
  currentLeadName?: string | null;
  children: React.ReactNode;
}

export function TransferLeadDialog({
  slug,
  projectId,
  targetMemberId,
  targetMemberName,
  actorIsLead = true,
  targetIsSelf = false,
  currentLeadName = null,
  children,
}: TransferLeadDialogProps) {
  const [open, setOpen] = useState(false);
  const [isTransferring, setIsTransferring] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const demotedLead = currentLeadName
    ? `${currentLeadName}, the current lead,`
    : "The current lead";

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
            {targetIsSelf ? (
              <>
                Make yourself the lead on this matter? {demotedLead} will become a regular member.
              </>
            ) : (
              <>
                Transfer the lead role to{" "}
                <span className="text-foreground font-semibold">{targetMemberName}</span>?{" "}
                {actorIsLead
                  ? "You will become a regular member."
                  : `${demotedLead} will become a regular member.`}
              </>
            )}
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
