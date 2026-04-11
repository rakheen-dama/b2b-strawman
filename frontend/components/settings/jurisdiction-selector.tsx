"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
import { updateDataProtectionSettings } from "@/app/(app)/org/[slug]/settings/data-protection/actions";

const JURISDICTIONS = [
  { value: "ZA", label: "South Africa (POPIA)", disabled: false },
  { value: "EU", label: "European Union (GDPR)", disabled: true },
  { value: "BR", label: "Brazil (LGPD)", disabled: true },
] as const;

interface JurisdictionSelectorSectionProps {
  slug: string;
  currentJurisdiction: string | null;
}

export function JurisdictionSelectorSection({
  slug,
  currentJurisdiction,
}: JurisdictionSelectorSectionProps) {
  const router = useRouter();
  const [pendingJurisdiction, setPendingJurisdiction] = useState<string | null>(
    currentJurisdiction
  );
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submitJurisdiction(value: string) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await updateDataProtectionSettings(slug, {
        dataProtectionJurisdiction: value,
      });
      if (result.success) {
        router.refresh();
      } else {
        setError(result.error ?? "Failed to update jurisdiction.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleValueChange(value: string) {
    setPendingJurisdiction(value);
    setError(null);

    if (currentJurisdiction === null) {
      // First selection — show confirmation dialog
      setDialogOpen(true);
    } else {
      // Already has a jurisdiction — submit immediately
      submitJurisdiction(value);
    }
  }

  async function handleConfirm() {
    if (!pendingJurisdiction) return;
    setDialogOpen(false);
    await submitJurisdiction(pendingJurisdiction);
  }

  function handleCancel() {
    setPendingJurisdiction(currentJurisdiction);
    setDialogOpen(false);
  }

  return (
    <>
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Jurisdiction</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Select the data protection jurisdiction that applies to your organisation. This determines
          default retention policies and processing register entries.
        </p>

        <div className="mt-4 flex items-center gap-3">
          <Select
            value={pendingJurisdiction ?? ""}
            onValueChange={handleValueChange}
            disabled={isSubmitting}
          >
            <SelectTrigger className="w-64">
              <SelectValue placeholder="Select a jurisdiction" />
            </SelectTrigger>
            <SelectContent>
              {JURISDICTIONS.map((j) => (
                <SelectItem key={j.value} value={j.value} disabled={j.disabled}>
                  {j.label}
                  {j.disabled && <span className="ml-2 text-xs text-slate-400">(coming soon)</span>}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {isSubmitting && <span className="text-sm text-slate-500">Saving...</span>}
        </div>

        {error && <p className="text-destructive mt-3 text-sm">{error}</p>}
      </div>

      <AlertDialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Set Data Protection Jurisdiction</AlertDialogTitle>
            <AlertDialogDescription>
              Setting your jurisdiction will create default retention policies and processing
              register entries. This cannot be undone. Continue?
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={handleCancel} disabled={isSubmitting}>
              Cancel
            </AlertDialogCancel>
            <Button onClick={handleConfirm} disabled={isSubmitting}>
              {isSubmitting ? "Applying..." : "Confirm"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
