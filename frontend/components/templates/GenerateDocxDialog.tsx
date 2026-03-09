"use client";

import { useState } from "react";
import { AlertTriangle, CheckCircle2, Download, FileText, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { generateDocxAction } from "@/app/(app)/org/[slug]/settings/templates/actions";
import type { GenerateDocxResult, OutputFormat } from "@/lib/types";

interface GenerateDocxDialogProps {
  templateId: string;
  templateName: string;
  entityId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onGenerated?: () => void;
}

type DialogState = "idle" | "generating" | "success" | "error";

const OUTPUT_FORMAT_OPTIONS: { value: OutputFormat; label: string }[] = [
  { value: "DOCX", label: "Word Document (.docx)" },
  { value: "PDF", label: "PDF" },
  { value: "BOTH", label: "Both (Word + PDF)" },
];

export function GenerateDocxDialog({
  templateId,
  templateName,
  entityId,
  open,
  onOpenChange,
  onGenerated,
}: GenerateDocxDialogProps) {
  const [outputFormat, setOutputFormat] = useState<OutputFormat>("DOCX");
  const [state, setState] = useState<DialogState>("idle");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<GenerateDocxResult | null>(null);

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      // Reset state when closing
      setState("idle");
      setError(null);
      setResult(null);
      setOutputFormat("DOCX");
    }
    onOpenChange(nextOpen);
  }

  async function handleGenerate() {
    setState("generating");
    setError(null);

    try {
      const response = await generateDocxAction(templateId, entityId, outputFormat);
      if (response.success && response.data) {
        setResult(response.data);
        setState("success");
        onGenerated?.();
        window.dispatchEvent(new Event("generated-documents-refresh"));
      } else {
        setError(response.error ?? "Failed to generate document.");
        setState("error");
      }
    } catch {
      setError("An unexpected error occurred.");
      setState("error");
    }
  }

  const pdfRequested = outputFormat === "PDF" || outputFormat === "BOTH";
  const pdfUnavailable = result && pdfRequested && !result.pdfDownloadUrl;
  const hasWarnings = result && result.warnings.length > 0;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileText className="size-5" />
            Generate: {templateName}
          </DialogTitle>
        </DialogHeader>

        {state === "idle" && (
          <div className="space-y-4">
            <div>
              <p className="mb-3 text-sm font-medium text-slate-700 dark:text-slate-300">
                Output Format
              </p>
              <div className="space-y-2">
                {OUTPUT_FORMAT_OPTIONS.map((option) => (
                  <label
                    key={option.value}
                    className="flex cursor-pointer items-center gap-2"
                  >
                    <input
                      type="radio"
                      name="outputFormat"
                      value={option.value}
                      checked={outputFormat === option.value}
                      onChange={() => setOutputFormat(option.value)}
                      className="size-4 accent-teal-600"
                    />
                    <span className="text-sm text-slate-700 dark:text-slate-300">
                      {option.label}
                    </span>
                  </label>
                ))}
              </div>
            </div>
          </div>
        )}

        {state === "generating" && (
          <div className="flex h-32 items-center justify-center">
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <Loader2 className="size-5 animate-spin" />
              Generating document...
            </div>
          </div>
        )}

        {state === "success" && result && (
          <div className="space-y-4">
            <div className="flex items-center gap-2 text-sm text-green-600 dark:text-green-400">
              <CheckCircle2 className="size-5" />
              Document generated successfully
            </div>

            <div className="flex flex-col gap-2">
              {result.downloadUrl && (
                <Button
                  variant="outline"
                  className="justify-start"
                  asChild
                >
                  <a
                    href={result.downloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <Download className="mr-1.5 size-4" />
                    Download .docx
                  </a>
                </Button>
              )}

              {result.pdfDownloadUrl && (
                <Button
                  variant="outline"
                  className="justify-start"
                  asChild
                >
                  <a
                    href={result.pdfDownloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <Download className="mr-1.5 size-4" />
                    Download PDF
                  </a>
                </Button>
              )}
            </div>

            {pdfUnavailable && (
              <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-3 dark:border-yellow-900 dark:bg-yellow-950/50">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="mt-0.5 size-4 shrink-0 text-yellow-600" />
                  <p className="text-sm text-yellow-800 dark:text-yellow-200">
                    PDF conversion is not available. Your document has been
                    generated as .docx
                  </p>
                </div>
              </div>
            )}

            {hasWarnings && (
              <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-3 dark:border-yellow-900 dark:bg-yellow-950/50">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="mt-0.5 size-4 shrink-0 text-yellow-600" />
                  <div className="space-y-1">
                    {result.warnings.map((warning, idx) => (
                      <p
                        key={idx}
                        className="text-sm text-yellow-800 dark:text-yellow-200"
                      >
                        {warning}
                      </p>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {state === "error" && (
          <div className="space-y-4">
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
        )}

        <DialogFooter>
          {state === "idle" && (
            <Button variant="accent" onClick={handleGenerate}>
              Generate
            </Button>
          )}
          {state === "error" && (
            <Button
              variant="accent"
              onClick={() => {
                setState("idle");
                setError(null);
              }}
            >
              Try Again
            </Button>
          )}
          {state === "success" && (
            <Button variant="outline" onClick={() => handleOpenChange(false)}>
              Close
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
