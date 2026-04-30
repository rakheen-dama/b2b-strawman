"use client";

import { cloneElement, isValidElement, useState } from "react";
import type { MouseEvent, ReactElement } from "react";
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
import { archiveCustomer } from "@/app/(app)/org/[slug]/customers/actions";
import { useRouter } from "next/navigation";
import { AlertTriangle } from "lucide-react";

interface ArchiveCustomerDialogProps {
  slug: string;
  customerId: string;
  customerName: string;
  children: React.ReactNode;
}

export function ArchiveCustomerDialog({
  slug,
  customerId,
  customerName,
  children,
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

  // OBS-2103: avoid Radix `Slot` (`asChild`) here. The Archive dialog renders
  // adjacent to <EditCustomerDialog/> on the customer detail page; under
  // React 19 + radix-ui, two unkeyed sibling Slots cloning a <Button> child
  // (Dialog vs AlertDialog) collapse during commit and only one survives in
  // the DOM. Inject the open handler via React.cloneElement instead so the
  // child renders as a plain <Button> with no Slot wrapper.
  const trigger = isValidElement<{ onClick?: (event: MouseEvent<HTMLElement>) => void }>(children)
    ? cloneElement(children as ReactElement<{ onClick?: (event: MouseEvent<HTMLElement>) => void }>, {
        onClick: (event: MouseEvent<HTMLElement>) => {
          children.props.onClick?.(event);
          if (!event.defaultPrevented) {
            setOpen(true);
          }
        },
      })
    : children;

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      {trigger}
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
