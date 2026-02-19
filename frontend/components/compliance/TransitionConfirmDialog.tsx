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
import { AlertTriangle } from "lucide-react";
import { transitionCustomerLifecycle } from "@/app/(app)/org/[slug]/customers/[id]/lifecycle-actions";

interface TransitionConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  slug: string;
  customerId: string;
  targetStatus: string;
  onSuccess?: () => void;
}

interface TransitionMeta {
  title: string;
  description: string;
  confirmLabel: string;
  isDestructive: boolean;
  requiresNotes: boolean;
}

const TRANSITION_META: Record<string, TransitionMeta> = {
  ONBOARDING: {
    title: "Start Onboarding",
    description:
      "This will move the customer to Onboarding status and automatically create compliance checklists.",
    confirmLabel: "Start Onboarding",
    isDestructive: false,
    requiresNotes: false,
  },
  ACTIVE: {
    title: "Activate Customer",
    description:
      "This will mark the customer as Active. All onboarding checklists must be completed before activation.",
    confirmLabel: "Activate",
    isDestructive: false,
    requiresNotes: false,
  },
  DORMANT: {
    title: "Mark as Dormant",
    description:
      "This will mark the customer as Dormant. They will be flagged for dormancy review.",
    confirmLabel: "Mark as Dormant",
    isDestructive: false,
    requiresNotes: false,
  },
  OFFBOARDING: {
    title: "Offboard Customer",
    description:
      "This will begin the offboarding process. The customer will become read-only and no new work can be created.",
    confirmLabel: "Begin Offboarding",
    isDestructive: true,
    requiresNotes: false,
  },
  OFFBOARDED: {
    title: "Complete Offboarding",
    description:
      "This will mark the customer as fully Offboarded. This is a permanent status change.",
    confirmLabel: "Complete Offboarding",
    isDestructive: true,
    requiresNotes: false,
  },
};

export function TransitionConfirmDialog({
  open,
  onOpenChange,
  slug,
  customerId,
  targetStatus,
  onSuccess,
}: TransitionConfirmDialogProps) {
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notes, setNotes] = useState("");

  const meta = TRANSITION_META[targetStatus] ?? {
    title: `Transition to ${targetStatus}`,
    description: `Confirm transition to ${targetStatus}.`,
    confirmLabel: "Confirm",
    isDestructive: false,
    requiresNotes: false,
  };

  async function handleConfirm() {
    setError(null);
    setIsPending(true);

    try {
      const result = await transitionCustomerLifecycle(
        slug,
        customerId,
        targetStatus,
        notes.trim() || undefined,
      );
      if (result.success) {
        onOpenChange(false);
        setNotes("");
        onSuccess?.();
      } else {
        setError(result.error ?? "Transition failed.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsPending(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (!newOpen) {
      setError(null);
      setNotes("");
    }
    onOpenChange(newOpen);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent
        className={
          meta.isDestructive ? "border-t-4 border-t-red-500" : "border-t-4 border-t-slate-200"
        }
      >
        <AlertDialogHeader>
          {meta.isDestructive && (
            <div className="flex justify-center">
              <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
                <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
              </div>
            </div>
          )}
          <AlertDialogTitle className="text-center">{meta.title}</AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            {meta.description}
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-2">
          <label className="text-sm text-slate-600 dark:text-slate-400">
            Notes {meta.requiresNotes ? "(required)" : "(optional)"}
          </label>
          <Textarea
            placeholder="Add a reason for this transition..."
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            className="resize-none"
            rows={3}
          />
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isPending}>
            Cancel
          </AlertDialogCancel>
          <Button
            variant={meta.isDestructive ? "destructive" : "default"}
            onClick={handleConfirm}
            disabled={isPending || (meta.requiresNotes && !notes.trim())}
          >
            {isPending ? "Processing..." : meta.confirmLabel}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
