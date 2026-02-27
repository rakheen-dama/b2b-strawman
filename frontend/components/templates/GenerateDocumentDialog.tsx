"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, ArrowLeft, CheckCircle2, Download, Save, Send } from "lucide-react";
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
import { getTemplateClauses } from "@/lib/actions/template-clause-actions";
import type { TemplateClauseDetail } from "@/lib/actions/template-clause-actions";
import { GenerationClauseStep } from "@/components/templates/generation-clause-step";
import type { SelectedClause } from "@/components/templates/generation-clause-step";
import type { TemplateValidationResult } from "@/lib/types";
import { SendForAcceptanceDialog } from "@/components/acceptance/SendForAcceptanceDialog";

interface GenerateDocumentDialogProps {
  templateId: string;
  templateName: string;
  entityId: string;
  entityType?: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved?: () => void;
  customerId?: string;
  isAdmin?: boolean;
}

type DialogStep = "clauses" | "preview";

export function GenerateDocumentDialog({
  templateId,
  templateName,
  entityId,
  open,
  onOpenChange,
  onSaved,
  customerId,
  isAdmin = false,
}: GenerateDocumentDialogProps) {
  const [html, setHtml] = useState<string | null>(null);
  const [isLoadingPreview, setIsLoadingPreview] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [validationResult, setValidationResult] =
    useState<TemplateValidationResult | null>(null);
  const [savedDocumentId, setSavedDocumentId] = useState<string | null>(null);
  const [acceptanceDialogOpen, setAcceptanceDialogOpen] = useState(false);

  // Multi-step state
  const [hasClauses, setHasClauses] = useState<boolean | null>(null);
  const [templateClauseList, setTemplateClauseList] = useState<TemplateClauseDetail[]>([]);
  const [isCheckingClauses, setIsCheckingClauses] = useState(false);
  const [step, setStep] = useState<DialogStep>("preview");
  const [selectedClauses, setSelectedClauses] = useState<SelectedClause[]>([]);

  // Check if template has clause associations on open
  useEffect(() => {
    if (!open) {
      setHtml(null);
      setError(null);
      setSuccessMessage(null);
      setValidationResult(null);
      setHasClauses(null);
      setTemplateClauseList([]);
      setStep("preview");
      setSelectedClauses([]);
      setSavedDocumentId(null);
      setAcceptanceDialogOpen(false);
      return;
    }

    async function checkClauses() {
      setIsCheckingClauses(true);
      try {
        const clauses = await getTemplateClauses(templateId);
        const hasAssociations = clauses.length > 0;
        setHasClauses(hasAssociations);
        setTemplateClauseList(clauses);
        if (hasAssociations) {
          setStep("clauses");
        } else {
          setStep("preview");
        }
      } catch {
        // If we can't check clauses, default to no-clause flow
        setHasClauses(false);
        setTemplateClauseList([]);
        setStep("preview");
      } finally {
        setIsCheckingClauses(false);
      }
    }

    checkClauses();
  }, [open, templateId]);

  function clauseSelectionsForApi(clauses?: SelectedClause[]) {
    const list = clauses ?? selectedClauses;
    if (!hasClauses || list.length === 0) return undefined;
    return list.map((c) => ({
      clauseId: c.clauseId,
      sortOrder: c.sortOrder,
    }));
  }

  // Load preview imperatively (called from handlers and effect)
  async function loadPreview(clauseOverrides?: SelectedClause[]) {
    setIsLoadingPreview(true);
    setError(null);
    setHtml(null);

    try {
      const result = await previewTemplateAction(
        templateId,
        entityId,
        clauseSelectionsForApi(clauseOverrides),
      );
      if (result.success && result.html) {
        setHtml(result.html);
        if (result.validationResult) {
          setValidationResult(result.validationResult);
        }
      } else {
        setError(result.error ?? "Failed to generate preview.");
      }
    } catch {
      setError("An unexpected error occurred while loading preview.");
    } finally {
      setIsLoadingPreview(false);
    }
  }

  // Auto-load preview for no-clause flow only
  useEffect(() => {
    if (!open || isCheckingClauses || hasClauses !== false || step !== "preview") return;
    loadPreview();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, isCheckingClauses, hasClauses, step]);

  const hasWarnings = Boolean(
    validationResult && !validationResult.allPresent,
  );

  function handleClauseNext(clauses: SelectedClause[]) {
    setSelectedClauses(clauses);
    setStep("preview");
    loadPreview(clauses);
  }

  function handleBackToClauses() {
    setStep("clauses");
    setHtml(null);
    setError(null);
    setValidationResult(null);
  }

  async function handleDownload() {
    setIsDownloading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const result = await generateDocumentAction(
        templateId,
        entityId,
        false,
        hasWarnings,
        clauseSelectionsForApi(),
      );
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
      const result = await generateDocumentAction(
        templateId,
        entityId,
        true,
        hasWarnings,
        clauseSelectionsForApi(),
      );
      if (result.success) {
        setSuccessMessage("Document saved successfully");
        onSaved?.();
        // Notify any GeneratedDocumentsList instances to refresh
        window.dispatchEvent(new Event("generated-documents-refresh"));

        const canSendForAcceptance = isAdmin && !!customerId && result.data?.id;
        if (canSendForAcceptance) {
          // Store saved document ID so user can send for acceptance
          setSavedDocumentId(result.data!.id);
        } else {
          // Close after a brief moment so the user sees the success message
          setTimeout(() => {
            onOpenChange(false);
          }, 800);
        }
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
  const totalSteps = hasClauses ? 2 : 1;
  const currentStep = step === "clauses" ? 1 : hasClauses ? 2 : 1;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>
            Generate: {templateName}
            {totalSteps > 1 && (
              <span className="ml-2 text-sm font-normal text-slate-500">
                Step {currentStep} of {totalSteps}
              </span>
            )}
          </DialogTitle>
        </DialogHeader>

        {isCheckingClauses && (
          <div className="flex h-48 items-center justify-center">
            <p className="text-sm text-slate-500">Loading...</p>
          </div>
        )}

        {!isCheckingClauses && step === "clauses" && (
          <GenerationClauseStep
            templateId={templateId}
            preloadedClauses={templateClauseList}
            initialClauses={
              selectedClauses.length > 0 ? selectedClauses : undefined
            }
            onNext={handleClauseNext}
          />
        )}

        {!isCheckingClauses && step === "preview" && (
          <>
            <div className="space-y-4">
              {isLoadingPreview && (
                <div className="flex h-[500px] items-center justify-center rounded-lg border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                  <p className="text-sm text-slate-500">
                    Generating preview...
                  </p>
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

              {validationResult && !validationResult.allPresent && (
                <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-3 dark:border-yellow-900 dark:bg-yellow-950/50">
                  <p className="mb-2 text-sm font-medium text-yellow-800 dark:text-yellow-200">
                    Required field warnings
                  </p>
                  <ul className="space-y-1">
                    {validationResult.fields.map((f, idx) => (
                      <li
                        key={idx}
                        className="flex items-center gap-2 text-sm"
                      >
                        {f.present ? (
                          <CheckCircle2 className="size-4 shrink-0 text-green-600" />
                        ) : (
                          <AlertTriangle className="size-4 shrink-0 text-yellow-600" />
                        )}
                        <span
                          className={
                            f.present
                              ? "text-slate-700 dark:text-slate-300"
                              : "text-yellow-800 dark:text-yellow-200"
                          }
                        >
                          {f.entity}.{f.field}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {error && <p className="text-sm text-destructive">{error}</p>}
              {successMessage && (
                <p className="text-sm text-green-600 dark:text-green-400">
                  {successMessage}
                </p>
              )}
              {savedDocumentId && customerId && (
                <div className="flex items-center gap-2">
                  <Button
                    variant="accent"
                    size="sm"
                    onClick={() => setAcceptanceDialogOpen(true)}
                  >
                    <Send className="mr-1.5 size-4" />
                    Send for Acceptance
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onOpenChange(false)}
                  >
                    Close
                  </Button>
                </div>
              )}
            </div>

            {!savedDocumentId && (
              <DialogFooter>
                {hasClauses && (
                  <Button
                    variant="ghost"
                    onClick={handleBackToClauses}
                    disabled={isActionInProgress}
                    className="mr-auto"
                  >
                    <ArrowLeft className="mr-1.5 size-4" />
                    Back
                  </Button>
                )}
                <Button
                  variant="outline"
                  onClick={handleDownload}
                  disabled={isActionInProgress || isLoadingPreview}
                >
                  <Download className="mr-1.5 size-4" />
                  {isDownloading
                    ? "Downloading..."
                    : hasWarnings
                      ? "Download anyway"
                      : "Download PDF"}
                </Button>
                <Button
                  variant={hasWarnings ? "destructive" : "accent"}
                  onClick={handleSave}
                  disabled={isActionInProgress || isLoadingPreview}
                >
                  <Save className="mr-1.5 size-4" />
                  {isSaving
                    ? "Saving..."
                    : hasWarnings
                      ? "Save anyway"
                      : "Save to Documents"}
                </Button>
              </DialogFooter>
            )}
          </>
        )}
      </DialogContent>

      {savedDocumentId && customerId && (
        <SendForAcceptanceDialog
          generatedDocumentId={savedDocumentId}
          customerId={customerId}
          documentName={templateName}
          open={acceptanceDialogOpen}
          onOpenChange={(open) => {
            setAcceptanceDialogOpen(open);
            if (!open) {
              onOpenChange(false);
            }
          }}
        />
      )}
    </Dialog>
  );
}
