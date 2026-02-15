"use client";

import { useState } from "react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { resetTemplateAction } from "@/app/(app)/org/[slug]/settings/templates/actions";

interface ResetTemplateDialogProps {
  slug: string;
  templateId: string;
  templateName: string;
  children: React.ReactNode;
}

export function ResetTemplateDialog({
  slug,
  templateId,
  templateName,
  children,
}: ResetTemplateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isResetting, setIsResetting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleReset() {
    setIsResetting(true);
    setError(null);

    try {
      const result = await resetTemplateAction(slug, templateId);

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to reset template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsResetting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Reset Template</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to reset &quot;{templateName}&quot;? This will
            delete your customized version and revert to the original platform
            template.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isResetting}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            onClick={handleReset}
            disabled={isResetting}
          >
            {isResetting ? "Resetting..." : "Reset to Default"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
