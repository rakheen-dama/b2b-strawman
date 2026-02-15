"use client";

import { useEffect, useState } from "react";
import { Download, Save } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  previewTemplateAction,
  generateDocumentAction,
} from "@/app/(app)/org/[slug]/settings/templates/actions";

interface GenerateDocumentDialogProps {
  templateId: string;
  templateName: string;
  entityId: string;
  entityType?: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved?: () => void;
}

export function GenerateDocumentDialog({
  templateId,
  templateName,
  entityId,
  open,
  onOpenChange,
  onSaved,
}: GenerateDocumentDialogProps) {
  const [html, setHtml] = useState<string | null>(null);
  const [isLoadingPreview, setIsLoadingPreview] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setHtml(null);
      setError(null);
      setSuccessMessage(null);
      return;
    }

    async function loadPreview() {
      setIsLoadingPreview(true);
      setError(null);
      setHtml(null);

      try {
        const result = await previewTemplateAction(templateId, entityId);
        if (result.success && result.html) {
          setHtml(result.html);
        } else {
          setError(result.error ?? "Failed to generate preview.");
        }
      } catch {
        setError("An unexpected error occurred while loading preview.");
      } finally {
        setIsLoadingPreview(false);
      }
    }

    loadPreview();
  }, [open, templateId, entityId]);

  async function handleDownload() {
    setIsDownloading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const result = await generateDocumentAction(templateId, entityId, false);
      if (result.success && result.pdfBase64) {
        // Convert base64 to blob and trigger download
        const byteCharacters = atob(result.pdfBase64);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        const blob = new Blob([byteArray], { type: "application/pdf" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `${templateName}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      } else {
        setError(result.error ?? "Failed to download document.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDownloading(false);
    }
  }

  async function handleSave() {
    setIsSaving(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const result = await generateDocumentAction(templateId, entityId, true);
      if (result.success) {
        setSuccessMessage("Document saved successfully");
        onSaved?.();
        // Notify any GeneratedDocumentsList instances to refresh
        window.dispatchEvent(new Event("generated-documents-refresh"));
        // Close after a brief moment so the user sees the success message
        setTimeout(() => {
          onOpenChange(false);
        }, 800);
      } else {
        setError(result.error ?? "Failed to save document.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  const isActionInProgress = isDownloading || isSaving;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>Generate: {templateName}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {isLoadingPreview && (
            <div className="flex h-[500px] items-center justify-center rounded-lg border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
              <p className="text-sm text-slate-500">Generating preview...</p>
            </div>
          )}

          {html && (
            <div className="overflow-hidden rounded-lg border border-slate-200 dark:border-slate-800">
              <iframe
                sandbox=""
                srcDoc={html}
                className="h-[500px] w-full bg-white"
                title="Document Preview"
              />
            </div>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}
          {successMessage && (
            <p className="text-sm text-green-600 dark:text-green-400">
              {successMessage}
            </p>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={handleDownload}
            disabled={isActionInProgress || isLoadingPreview}
          >
            <Download className="mr-1.5 size-4" />
            {isDownloading ? "Downloading..." : "Download PDF"}
          </Button>
          <Button
            variant="accent"
            onClick={handleSave}
            disabled={isActionInProgress || isLoadingPreview}
          >
            <Save className="mr-1.5 size-4" />
            {isSaving ? "Saving..." : "Save to Documents"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
