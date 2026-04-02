"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
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
import { cancelSubscription } from "@/app/(app)/org/[slug]/settings/billing/actions";

interface CancelConfirmDialogProps {
  currentPeriodEnd: string;
  onCancel?: () => void;
}

export function CancelConfirmDialog({
  currentPeriodEnd,
  onCancel,
}: CancelConfirmDialogProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const formattedDate = new Date(currentPeriodEnd).toLocaleDateString("en-ZA", {
    dateStyle: "long",
  });

  async function handleConfirm() {
    setError(null);
    setIsLoading(true);

    try {
      await cancelSubscription();
      setOpen(false);
      onCancel?.();
      router.refresh();
    } catch (err) {
      const message =
        err instanceof Error
          ? err.message
          : "Failed to cancel subscription.";
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (isLoading) return;
    if (newOpen) {
      setError(null);
    }
    setOpen(newOpen);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger asChild>
        <Button variant="outline">Cancel Subscription</Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Cancel Subscription</AlertDialogTitle>
          <AlertDialogDescription>
            Your subscription will remain active until {formattedDate}. After
            that, your account enters a read-only grace period.
          </AlertDialogDescription>
        </AlertDialogHeader>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <AlertDialogFooter>
          <AlertDialogCancel disabled={isLoading}>
            Keep Subscription
          </AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={handleConfirm}
            disabled={isLoading}
          >
            {isLoading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Cancelling...
              </>
            ) : (
              "Confirm Cancellation"
            )}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
