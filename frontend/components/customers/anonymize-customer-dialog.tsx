"use client";

import { useState } from "react";
import useSWR from "swr";
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
import { Input } from "@/components/ui/input";
import { AlertTriangle, ShieldCheck, Loader2 } from "lucide-react";
import {
  fetchAnonymizationPreview,
  executeAnonymization,
} from "@/app/(app)/org/[slug]/customers/[id]/data-protection-actions";
import { useRouter } from "next/navigation";
import type { StandaloneAnonymizationResult } from "@/lib/types/data-protection";

type Step = "preview" | "confirm" | "result";

interface AnonymizeCustomerDialogProps {
  slug: string;
  customerId: string;
  customerName: string;
  children: React.ReactNode;
}

export function AnonymizeCustomerDialog({
  slug,
  customerId,
  customerName,
  children,
}: AnonymizeCustomerDialogProps) {
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState<Step>("preview");
  const [confirmInput, setConfirmInput] = useState("");
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<StandaloneAnonymizationResult | null>(null);
  const router = useRouter();

  const {
    data: previewResult,
    error: previewError,
    isLoading: previewLoading,
  } = useSWR(
    open && step === "preview" ? `anonymize-preview-${customerId}` : null,
    () => fetchAnonymizationPreview(customerId),
  );

  const preview = previewResult?.data;
  const isConfirmed = confirmInput === customerName;

  function handleOpenChange(newOpen: boolean) {
    if (!newOpen) {
      setStep("preview");
      setConfirmInput("");
      setError(null);
      setResult(null);
      setIsPending(false);
    }
    setOpen(newOpen);
  }

  async function handleExecute() {
    if (!isConfirmed) return;
    setError(null);
    setIsPending(true);

    try {
      const res = await executeAnonymization(slug, customerId, confirmInput, "Data subject request");
      if (res.success && res.data) {
        setResult(res.data);
        setStep("result");
      } else {
        setError(res.error ?? "Failed to anonymize customer.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsPending(false);
    }
  }

  function handleClose() {
    handleOpenChange(false);
    if (result) {
      router.refresh();
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent className="border-t-4 border-t-red-500 max-w-lg">
        {step === "preview" && (
          <>
            <AlertDialogHeader>
              <div className="flex justify-center">
                <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
                  <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
                </div>
              </div>
              <AlertDialogTitle className="text-center">Anonymize Customer</AlertDialogTitle>
              <AlertDialogDescription className="text-center">
                Review the data that will be affected by anonymizing{" "}
                <span className="text-foreground font-semibold">{customerName}</span>.
              </AlertDialogDescription>
            </AlertDialogHeader>

            {previewLoading && (
              <div className="flex justify-center py-6">
                <Loader2 className="size-6 animate-spin text-slate-400" />
              </div>
            )}

            {previewError && (
              <p className="text-sm text-destructive text-center">
                Failed to load anonymization preview.
              </p>
            )}

            {previewResult && !previewResult.success && (
              <p className="text-sm text-destructive text-center">
                {previewResult.error}
              </p>
            )}

            {preview && (
              <div className="space-y-4">
                <div className="space-y-2 text-sm text-slate-700 dark:text-slate-300">
                  <p className="font-medium">Affected entities:</p>
                  <ul className="list-disc pl-4 space-y-1 text-slate-600 dark:text-slate-400">
                    <li>Portal contacts: {preview.affectedEntities.portalContacts}</li>
                    <li>Projects: {preview.affectedEntities.projects}</li>
                    <li>Documents: {preview.affectedEntities.documents}</li>
                    <li>Time entries: {preview.affectedEntities.timeEntries}</li>
                    <li>Invoices: {preview.affectedEntities.invoices}</li>
                    <li>Comments: {preview.affectedEntities.comments}</li>
                  </ul>
                </div>

                {preview.financialRecordsRetained > 0 && (
                  <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
                    <p className="font-medium">Financial records retained</p>
                    <p className="mt-1">
                      {preview.financialRecordsRetained}{" "}
                      {preview.financialRecordsRetained === 1 ? "invoice" : "invoices"} will be
                      preserved for financial compliance
                      {preview.financialRetentionExpiresAt &&
                        ` until ${preview.financialRetentionExpiresAt}`}
                      .
                    </p>
                  </div>
                )}
              </div>
            )}

            <AlertDialogFooter>
              <AlertDialogCancel variant="plain">Cancel</AlertDialogCancel>
              <Button
                variant="destructive"
                onClick={() => setStep("confirm")}
                disabled={previewLoading || !!previewError || (previewResult != null && !previewResult.success)}
              >
                Continue
              </Button>
            </AlertDialogFooter>
          </>
        )}

        {step === "confirm" && (
          <>
            <AlertDialogHeader>
              <div className="flex justify-center">
                <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
                  <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
                </div>
              </div>
              <AlertDialogTitle className="text-center">Confirm Anonymization</AlertDialogTitle>
              <AlertDialogDescription className="text-center">
                This action is permanent and cannot be undone.
              </AlertDialogDescription>
            </AlertDialogHeader>

            <div className="space-y-3 text-sm text-slate-700 dark:text-slate-300">
              <p className="font-medium">The following will happen:</p>
              <ul className="list-disc pl-4 space-y-1 text-slate-600 dark:text-slate-400">
                <li>Customer PII will be anonymized</li>
                <li>Documents will be permanently deleted</li>
                <li>Comments will be redacted</li>
                <li>Portal contacts will be anonymized</li>
                <li>Financial records (invoices) will be preserved</li>
                <li>Customer will be marked as Anonymized</li>
              </ul>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Type{" "}
                <span className="font-semibold text-slate-900 dark:text-slate-100">
                  {customerName}
                </span>{" "}
                to confirm
              </label>
              <Input
                value={confirmInput}
                onChange={(e) => setConfirmInput(e.target.value)}
                placeholder={customerName}
                className="font-mono"
              />
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}

            <AlertDialogFooter>
              <AlertDialogCancel variant="plain" disabled={isPending}>
                Cancel
              </AlertDialogCancel>
              <Button
                variant="destructive"
                onClick={handleExecute}
                disabled={isPending || !isConfirmed}
              >
                {isPending ? "Executing..." : "Execute Anonymization"}
              </Button>
            </AlertDialogFooter>
          </>
        )}

        {step === "result" && result && (
          <>
            <AlertDialogHeader>
              <div className="flex justify-center">
                <div className="flex size-12 items-center justify-center rounded-full bg-green-100 dark:bg-green-950">
                  <ShieldCheck className="size-6 text-green-600 dark:text-green-400" />
                </div>
              </div>
              <AlertDialogTitle className="text-center">Anonymization Complete</AlertDialogTitle>
              <AlertDialogDescription className="text-center">
                Customer data has been anonymized successfully.
              </AlertDialogDescription>
            </AlertDialogHeader>

            <div className="space-y-2 text-sm text-slate-700 dark:text-slate-300">
              <p>Anonymization summary:</p>
              <ul className="list-disc pl-4 space-y-1 text-slate-600 dark:text-slate-400">
                <li>Customer anonymized: {result.summary.customerAnonymized ? "Yes" : "No"}</li>
                <li>Documents deleted: {result.summary.documentsDeleted}</li>
                <li>Comments redacted: {result.summary.commentsRedacted}</li>
                <li>Portal contacts anonymized: {result.summary.portalContactsAnonymized}</li>
                <li>Invoices preserved: {result.summary.invoicesPreserved}</li>
              </ul>
            </div>

            <AlertDialogFooter>
              <Button variant="default" onClick={handleClose}>
                Close
              </Button>
            </AlertDialogFooter>
          </>
        )}
      </AlertDialogContent>
    </AlertDialog>
  );
}
