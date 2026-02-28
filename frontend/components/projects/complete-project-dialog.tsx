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
import { completeProject } from "@/app/(app)/org/[slug]/projects/actions";
import { CheckCircle, AlertTriangle } from "lucide-react";

interface CompleteProjectDialogProps {
  slug: string;
  projectId: string;
  projectName: string;
  children: React.ReactNode;
}

export function CompleteProjectDialog({
  slug,
  projectId,
  projectName,
  children,
}: CompleteProjectDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showUnbilledConfirm, setShowUnbilledConfirm] = useState(false);

  async function handleComplete(acknowledgeUnbilledTime = false) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await completeProject(
        slug,
        projectId,
        acknowledgeUnbilledTime ? true : undefined,
      );
      if (result.success) {
        setOpen(false);
        setShowUnbilledConfirm(false);
      } else {
        const errorMsg = result.error ?? "Failed to complete project.";
        // Check if this is an unbilled time warning (409)
        if (errorMsg.toLowerCase().includes("unbilled")) {
          setShowUnbilledConfirm(true);
          setError(null);
        } else {
          // Blocking error (e.g., open tasks -- 400)
          setError(errorMsg);
        }
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen);
    if (!nextOpen) {
      setError(null);
      setShowUnbilledConfirm(false);
      setIsSubmitting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-teal-100 dark:bg-teal-950">
              {showUnbilledConfirm ? (
                <AlertTriangle className="size-6 text-amber-600 dark:text-amber-400" />
              ) : (
                <CheckCircle className="size-6 text-teal-600 dark:text-teal-400" />
              )}
            </div>
          </div>
          <AlertDialogTitle className="text-center">
            {showUnbilledConfirm ? "Unbilled Time Warning" : "Complete Project"}
          </AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            {showUnbilledConfirm ? (
              <>
                <span className="text-foreground font-semibold">
                  {projectName}
                </span>{" "}
                has unbilled time entries. Completing the project will not affect
                these entries, but they will remain unbilled.
              </>
            ) : (
              <>
                Mark{" "}
                <span className="text-foreground font-semibold">
                  {projectName}
                </span>{" "}
                as completed? This will prevent new tasks and time entries from
                being added.
              </>
            )}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && (
          <p className="text-destructive text-center text-sm">{error}</p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isSubmitting}>
            Cancel
          </AlertDialogCancel>
          {showUnbilledConfirm ? (
            <Button
              onClick={() => handleComplete(true)}
              disabled={isSubmitting}
              data-testid="complete-anyway-btn"
            >
              {isSubmitting ? "Completing..." : "Complete Anyway"}
            </Button>
          ) : (
            <Button
              onClick={() => handleComplete(false)}
              disabled={isSubmitting}
              data-testid="confirm-complete-btn"
            >
              {isSubmitting ? "Completing..." : "Complete"}
            </Button>
          )}
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
