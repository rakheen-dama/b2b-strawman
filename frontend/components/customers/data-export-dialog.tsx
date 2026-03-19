"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { triggerDataExport } from "@/app/(app)/org/[slug]/customers/[id]/data-protection-actions";
import type { StandaloneExportResult } from "@/lib/types/data-protection";
import { Download, Loader2, FileDown } from "lucide-react";

type Step = "confirm" | "processing" | "download" | "error";

interface DataExportDialogProps {
  customerId: string;
  children: React.ReactNode;
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

export function DataExportDialog({ customerId, children }: DataExportDialogProps) {
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState<Step>("confirm");
  const [exportResult, setExportResult] = useState<StandaloneExportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleOpenChange(newOpen: boolean) {
    if (!newOpen) {
      // Reset state on close
      setStep("confirm");
      setExportResult(null);
      setError(null);
    }
    setOpen(newOpen);
  }

  async function handleExport() {
    setStep("processing");
    setError(null);

    try {
      const result = await triggerDataExport(customerId);
      if (result.success && result.data) {
        setExportResult(result.data);
        setStep("download");
      } else {
        setError(result.error ?? "Failed to generate export.");
        setStep("error");
      }
    } catch {
      setError("An unexpected error occurred.");
      setStep("error");
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-md">
        {step === "confirm" && (
          <>
            <DialogHeader>
              <div className="flex justify-center">
                <div className="flex size-12 items-center justify-center rounded-full bg-teal-100 dark:bg-teal-950">
                  <Download className="size-6 text-teal-600 dark:text-teal-400" />
                </div>
              </div>
              <DialogTitle className="text-center">Download All Data</DialogTitle>
              <DialogDescription className="text-center">
                Export all customer data including profile information, documents, time entries,
                invoices, and comments into a downloadable archive.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
              <Button onClick={handleExport}>Export Now</Button>
            </DialogFooter>
          </>
        )}

        {step === "processing" && (
          <>
            <DialogHeader>
              <DialogTitle className="text-center">Generating Export</DialogTitle>
              <DialogDescription className="text-center">
                Gathering and packaging customer data...
              </DialogDescription>
            </DialogHeader>
            <div className="flex justify-center py-6">
              <Loader2 className="size-8 animate-spin text-teal-600 dark:text-teal-400" />
            </div>
          </>
        )}

        {step === "download" && exportResult && (
          <>
            <DialogHeader>
              <div className="flex justify-center">
                <div className="flex size-12 items-center justify-center rounded-full bg-green-100 dark:bg-green-950">
                  <FileDown className="size-6 text-green-600 dark:text-green-400" />
                </div>
              </div>
              <DialogTitle className="text-center">Export Ready</DialogTitle>
              <DialogDescription className="text-center">
                {exportResult.fileCount} {exportResult.fileCount === 1 ? "file" : "files"} &middot;{" "}
                {formatBytes(exportResult.totalSizeBytes)}
              </DialogDescription>
            </DialogHeader>
            <div className="flex justify-center">
              <Button asChild>
                <a href={exportResult.downloadUrl} target="_blank" rel="noopener noreferrer">
                  <Download className="mr-1.5 size-4" />
                  Download Archive
                </a>
              </Button>
            </div>
            <p className="text-center text-xs text-slate-500 dark:text-slate-400">
              Link expires in 24 hours
            </p>
            <DialogFooter>
              <Button variant="outline" onClick={() => handleOpenChange(false)}>
                Close
              </Button>
            </DialogFooter>
          </>
        )}

        {step === "error" && (
          <>
            <DialogHeader>
              <DialogTitle className="text-center">Export Failed</DialogTitle>
              <DialogDescription className="text-center">
                {error ?? "An unexpected error occurred."}
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => handleOpenChange(false)}>
                Close
              </Button>
              <Button onClick={handleExport}>Retry</Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
