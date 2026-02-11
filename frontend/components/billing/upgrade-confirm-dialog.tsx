"use client";

import { useState } from "react";
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

interface UpgradeConfirmDialogProps {
  onConfirm: () => Promise<void>;
  trigger: React.ReactNode;
}

export function UpgradeConfirmDialog({ onConfirm, trigger }: UpgradeConfirmDialogProps) {
  const [open, setOpen] = useState(false);
  const [isUpgrading, setIsUpgrading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
    setError(null);
    setIsUpgrading(true);

    try {
      await onConfirm();
      setOpen(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to upgrade plan.";
      setError(message);
    } finally {
      setIsUpgrading(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (isUpgrading) return;
    if (newOpen) {
      setError(null);
    }
    setOpen(newOpen);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger asChild>{trigger}</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Upgrade to Pro</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div className="space-y-3">
              <p>
                Upgrade your organization to the Pro plan to unlock:
              </p>
              <ul className="list-disc pl-5 space-y-1">
                <li>Dedicated database infrastructure</li>
                <li>Up to 10 team members</li>
                <li>Schema-level data isolation</li>
                <li>Priority support</li>
              </ul>
              <p>This action takes effect immediately.</p>
            </div>
          </AlertDialogDescription>
        </AlertDialogHeader>

        {error && (
          <p className="text-sm text-destructive">{error}</p>
        )}

        <AlertDialogFooter>
          <AlertDialogCancel disabled={isUpgrading}>Cancel</AlertDialogCancel>
          <Button onClick={handleConfirm} disabled={isUpgrading}>
            {isUpgrading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Upgrading…
              </>
            ) : (
              "Upgrade Now"
            )}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
