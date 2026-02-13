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
import { AlertTriangle } from "lucide-react";
import {
  deleteBillingRate,
  deleteCostRate,
} from "@/app/(app)/org/[slug]/settings/rates/actions";

interface DeleteRateDialogProps {
  slug: string;
  rateId: string;
  rateType: "billing" | "cost";
  memberName: string;
  children: React.ReactNode;
}

export function DeleteRateDialog({
  slug,
  rateId,
  rateType,
  memberName,
  children,
}: DeleteRateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    setError(null);
    setIsDeleting(true);

    try {
      const result =
        rateType === "billing"
          ? await deleteBillingRate(slug, rateId)
          : await deleteCostRate(slug, rateId);

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to delete rate.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDeleting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setError(null);
    }
    setOpen(newOpen);
  }

  const typeLabel = rateType === "billing" ? "billing" : "cost";

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
              <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <AlertDialogTitle className="text-center">
            Delete {rateType === "billing" ? "Billing" : "Cost"} Rate
          </AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            Delete the {typeLabel} rate for {memberName}? This action cannot be
            undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isDeleting}>
            Cancel
          </AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={handleDelete}
            disabled={isDeleting}
          >
            {isDeleting ? "Deleting..." : "Delete"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
