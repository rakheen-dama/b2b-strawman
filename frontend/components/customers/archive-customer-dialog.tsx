"use client";

import { useState, type ReactNode } from "react";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button, type buttonVariants } from "@/components/ui/button";
import type { VariantProps } from "class-variance-authority";
import { archiveCustomer } from "@/app/(app)/org/[slug]/customers/actions";
import { useRouter } from "next/navigation";
import { AlertTriangle } from "lucide-react";

type ButtonVariant = NonNullable<VariantProps<typeof buttonVariants>["variant"]>;
type ButtonSize = NonNullable<VariantProps<typeof buttonVariants>["size"]>;

interface ArchiveCustomerDialogProps {
  slug: string;
  customerId: string;
  customerName: string;
  /**
   * OBS-2103b: dialog owns the trigger button. See `edit-customer-dialog.tsx`
   * for the full rationale — adjacent cloneElement-injected onClicks under
   * React 19 lose one of the two siblings on commit, so the dialog renders
   * the `<Button>` itself rather than relying on consumer-supplied children.
   */
  triggerLabel: ReactNode;
  triggerVariant?: ButtonVariant;
  triggerSize?: ButtonSize;
  triggerClassName?: string;
  triggerIcon?: ReactNode;
}

export function ArchiveCustomerDialog({
  slug,
  customerId,
  customerName,
  triggerLabel,
  triggerVariant = "ghost",
  triggerSize = "sm",
  triggerClassName,
  triggerIcon,
}: ArchiveCustomerDialogProps) {
  const [open, setOpen] = useState(false);
  const [isArchiving, setIsArchiving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();

  async function handleArchive() {
    setError(null);
    setIsArchiving(true);

    try {
      const result = await archiveCustomer(slug, customerId);
      if (result.success) {
        setOpen(false);
        router.push(`/org/${slug}/customers`);
      } else {
        setError(result.error ?? "Failed to archive customer.");
        setIsArchiving(false);
      }
    } catch {
      setError("An unexpected error occurred.");
      setIsArchiving(false);
    }
  }

  // OBS-2103 / OBS-2103b: dialog owns the trigger button. See
  // `edit-customer-dialog.tsx` for the full rationale.
  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <Button
        type="button"
        variant={triggerVariant}
        size={triggerSize}
        className={triggerClassName}
        onClick={() => setOpen(true)}
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
          <AlertDialogTitle className="text-center">Archive Customer</AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            Archive <span className="text-foreground font-semibold">{customerName}</span>? Their
            project links will be preserved but they will be hidden from active customer lists.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-destructive text-sm">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isArchiving}>
            Cancel
          </AlertDialogCancel>
          <Button variant="destructive" onClick={handleArchive} disabled={isArchiving}>
            {isArchiving ? "Archiving..." : "Archive"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
