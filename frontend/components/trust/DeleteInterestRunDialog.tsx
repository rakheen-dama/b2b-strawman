"use client";

import { useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { AlertTriangle } from "lucide-react";
import type { VariantProps } from "class-variance-authority";
import { Button, type buttonVariants } from "@b2mash/ui/button";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { deleteInterestRun } from "@/app/(app)/org/[slug]/trust-accounting/interest/actions";
import { formatLocalDate } from "@/lib/format";

type ButtonVariant = NonNullable<VariantProps<typeof buttonVariants>["variant"]>;
type ButtonSize = NonNullable<VariantProps<typeof buttonVariants>["size"]>;

interface DeleteInterestRunDialogProps {
  runId: string;
  periodStart: string;
  periodEnd: string;
  /**
   * OBS-2103: the dialog owns its trigger button — it sits beside the row's Continue / Review
   * button, and adjacent cloneElement-injected triggers lose a click handler under React 19.
   */
  triggerLabel?: ReactNode;
  triggerVariant?: ButtonVariant;
  triggerSize?: ButtonSize;
  triggerClassName?: string;
  triggerIcon?: ReactNode;
}

/**
 * Confirms deleting a mistaken DRAFT interest run (630B, founder ruling §9.2, ADR-362). The
 * backend refuses anything but a DRAFT; its ProblemDetail is surfaced verbatim.
 */
export function DeleteInterestRunDialog({
  runId,
  periodStart,
  periodEnd,
  triggerLabel = "Delete draft",
  triggerVariant = "ghost",
  triggerSize = "sm",
  triggerClassName,
  triggerIcon,
}: DeleteInterestRunDialogProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) setError(null);
    setOpen(newOpen);
  }

  async function handleDelete() {
    setError(null);
    setIsDeleting(true);
    try {
      const result = await deleteInterestRun(runId);
      if (result.success) {
        setOpen(false);
        toast.success("Draft interest run deleted");
        router.refresh();
      } else {
        // The backend ProblemDetail detail, verbatim (e.g. "Only DRAFT interest runs can be deleted")
        const message = result.error ?? "Failed to delete the draft interest run.";
        setError(message);
        toast.error(message);
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <Button
        type="button"
        variant={triggerVariant}
        size={triggerSize}
        className={triggerClassName}
        onClick={() => handleOpenChange(true)}
      >
        {triggerIcon}
        {triggerLabel}
      </Button>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
              <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <AlertDialogTitle className="text-center">Delete draft interest run?</AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            The draft for{" "}
            <span className="text-foreground font-semibold">
              {formatLocalDate(periodStart)} — {formatLocalDate(periodEnd)}
            </span>{" "}
            will be deleted. Its calculated allocations are discarded. Nothing has been posted.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-destructive text-sm">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isDeleting}>
            Cancel
          </AlertDialogCancel>
          <Button variant="destructive" onClick={handleDelete} disabled={isDeleting}>
            {isDeleting ? "Deleting..." : "Delete"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
