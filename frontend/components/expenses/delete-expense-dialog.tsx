"use client";

import { useState, useTransition, type ReactNode } from "react";
import type { VariantProps } from "class-variance-authority";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { deleteExpense } from "@/app/(app)/org/[slug]/projects/[id]/expense-actions";

type ButtonVariant = NonNullable<VariantProps<typeof buttonVariants>["variant"]>;
type ButtonSize = NonNullable<VariantProps<typeof buttonVariants>["size"]>;

interface DeleteExpenseDialogProps {
  slug: string;
  projectId: string;
  expenseId: string;
  expenseDescription: string;
  triggerLabel: ReactNode;
  triggerVariant?: ButtonVariant;
  triggerSize?: ButtonSize;
  triggerClassName?: string;
  triggerIcon?: ReactNode;
  triggerAriaLabel?: string;
}

export function DeleteExpenseDialog({
  slug,
  projectId,
  expenseId,
  expenseDescription,
  triggerLabel,
  triggerVariant = "ghost",
  triggerSize = "xs",
  triggerClassName,
  triggerIcon,
  triggerAriaLabel,
}: DeleteExpenseDialogProps) {
  const [open, setOpen] = useState(false);
  const [isPending, startTransition] = useTransition();

  function handleDelete() {
    startTransition(async () => {
      await deleteExpense(slug, projectId, expenseId);
      setOpen(false);
    });
  }

  // OBS-2103 / OBS-2103b / audit-03 sweep: dialog owns the trigger button.
  // Extracted out of expense-list.tsx so the inline AlertDialog no longer
  // sits as an asChild Slot adjacent to LogExpenseDialog's asChild Slot.
  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <Button
        type="button"
        variant={triggerVariant}
        size={triggerSize}
        className={triggerClassName}
        aria-label={triggerAriaLabel}
        onClick={() => setOpen(true)}
      >
        {triggerIcon}
        {triggerLabel}
      </Button>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete expense?</AlertDialogTitle>
          <AlertDialogDescription>
            This will permanently delete the expense &quot;
            {expenseDescription}
            &quot;. This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction onClick={handleDelete} disabled={isPending}>
            {isPending ? "Deleting..." : "Delete"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
