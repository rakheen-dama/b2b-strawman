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
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { AlertTriangle } from "lucide-react";
import { executeDeletion } from "@/app/(app)/org/[slug]/compliance/requests/actions";
import type { AnonymizationResult } from "@/lib/types";

interface DeletionConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  slug: string;
  requestId: string;
  customerName: string;
  onSuccess?: (summary: AnonymizationResult["anonymizationSummary"]) => void;
}

export function DeletionConfirmDialog({
  open,
  onOpenChange,
  slug,
  requestId,
  customerName,
  onSuccess,
}: DeletionConfirmDialogProps) {
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmInput, setConfirmInput] = useState("");
  const [summary, setSummary] = useState<AnonymizationResult["anonymizationSummary"] | null>(null);

  const isConfirmed = confirmInput === customerName;

  function handleOpenChange(newOpen: boolean) {
    if (!newOpen) {
      setError(null);
      setConfirmInput("");
      setSummary(null);
    }
    onOpenChange(newOpen);
  }

  async function handleConfirm() {
    if (!isConfirmed) return;
    setError(null);
    setIsPending(true);

    try {
      const result = await executeDeletion(slug, requestId, confirmInput);
      if (result.success && result.summary) {
        setSummary(result.summary);
        onSuccess?.(result.summary);
      } else {
        setError(result.error ?? "Failed to execute data deletion.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsPending(false);
    }
  }

  function handleClose() {
    handleOpenChange(false);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent className="border-t-4 border-t-red-500 max-w-md">
        {summary ? (
          <>
            <AlertDialogHeader>
              <AlertDialogTitle className="text-center">Deletion Complete</AlertDialogTitle>
              <AlertDialogDescription className="text-center">
                The customer data has been anonymized.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <div className="space-y-2 text-sm text-slate-700 dark:text-slate-300">
              <p>Anonymization summary:</p>
              <ul className="list-disc pl-4 space-y-1 text-slate-600 dark:text-slate-400">
                <li>Customer anonymized: {summary.customerAnonymized ? "Yes" : "No"}</li>
                <li>Documents deleted: {summary.documentsDeleted}</li>
                <li>Comments redacted: {summary.commentsRedacted}</li>
                <li>Portal contacts anonymized: {summary.portalContactsAnonymized}</li>
                <li>Invoices preserved: {summary.invoicesPreserved}</li>
              </ul>
            </div>
            <AlertDialogFooter>
              <Button variant="default" onClick={handleClose}>
                Close
              </Button>
            </AlertDialogFooter>
          </>
        ) : (
          <>
            <AlertDialogHeader>
              <div className="flex justify-center">
                <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
                  <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
                </div>
              </div>
              <AlertDialogTitle className="text-center">Execute Data Deletion</AlertDialogTitle>
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
                <li>Financial records (invoices, time entries) will be preserved</li>
                <li>Customer will be marked as Offboarded</li>
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
                onClick={handleConfirm}
                disabled={isPending || !isConfirmed}
              >
                {isPending ? "Executing..." : "Execute Deletion"}
              </Button>
            </AlertDialogFooter>
          </>
        )}
      </AlertDialogContent>
    </AlertDialog>
  );
}
