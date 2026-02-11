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

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
              <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <AlertDialogTitle className="text-center">Archive Customer</AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            Archive{" "}
            <span className="text-foreground font-semibold">{customerName}</span>? Their project
            links will be preserved but they will be hidden from active customer lists.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
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
