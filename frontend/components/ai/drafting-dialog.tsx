"use client";

import { useState, useTransition } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Checkbox } from "@/components/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { DraftingVariableTable } from "@/components/ai/drafting-variable-table";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import { invokeDraftingAction } from "@/app/(app)/org/[slug]/projects/[id]/ai-review-actions";
import { approveGateAction, rejectGateAction } from "@/app/(app)/org/[slug]/ai/reviews/actions";
import { draftingVariableFillsSchema } from "@/lib/schemas/drafting";
import {
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  FileText,
  Loader2,
  Sparkles,
} from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import type { DraftingResponse, VariableFill } from "@/lib/api/ai";
import type { TemplateListResponse } from "@/lib/types";

interface DraftingDialogProps {
  projectId: string;
  slug: string;
  isOpen: boolean;
  onClose: () => void;
  templates: TemplateListResponse[];
  canReviewGates?: boolean;
}

type Step = "SELECT_TEMPLATE" | "LOADING" | "RESULTS" | "CONFIRMED";

function formatZarCents(cents: number): string {
  return `R ${(cents / 100).toFixed(2)}`;
}

export function DraftingDialog({
  projectId,
  slug,
  isOpen,
  onClose,
  templates,
  canReviewGates = false,
}: DraftingDialogProps) {
  const [step, setStep] = useState<Step>("SELECT_TEMPLATE");
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>("");
  const [result, setResult] = useState<DraftingResponse | null>(null);
  const [variableFills, setVariableFills] = useState<VariableFill[]>([]);
  const [selectedClauses, setSelectedClauses] = useState<Set<string>>(new Set());
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [, startTransition] = useTransition();

  function handleOpenChange(open: boolean) {
    if (!open) {
      // Reset all state when closing
      setStep("SELECT_TEMPLATE");
      setSelectedTemplateId("");
      setResult(null);
      setVariableFills([]);
      setSelectedClauses(new Set());
      setExpandedSections(new Set());
      setError(null);
      onClose();
    }
  }

  function handleInvoke() {
    if (!selectedTemplateId) return;

    setStep("LOADING");
    setError(null);

    startTransition(async () => {
      try {
        const actionResult = await invokeDraftingAction(slug, projectId, selectedTemplateId);
        if (actionResult.success && actionResult.data) {
          setResult(actionResult.data);
          setVariableFills(actionResult.data.output?.variableFills ?? []);
          // Pre-select all clause recommendations
          const clauseIds = new Set(
            (actionResult.data.output?.clauseRecommendations ?? []).map((c) => c.clauseId)
          );
          setSelectedClauses(clauseIds);
          setStep("RESULTS");
        } else {
          setError(actionResult.error ?? "Drafting failed.");
          setStep("SELECT_TEMPLATE");
        }
      } catch {
        setError("Drafting failed unexpectedly. Please try again.");
        setStep("SELECT_TEMPLATE");
      }
    });
  }

  function handleConfirm() {
    const validation = draftingVariableFillsSchema.safeParse({ variableFills });
    if (!validation.success) {
      const firstError = validation.error.issues[0];
      toast.error(firstError?.message ?? "Variable fills validation failed.");
      return;
    }
    setStep("CONFIRMED");
  }

  function toggleSection(sectionName: string) {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(sectionName)) {
        next.delete(sectionName);
      } else {
        next.add(sectionName);
      }
      return next;
    });
  }

  function toggleClause(clauseId: string) {
    setSelectedClauses((prev) => {
      const next = new Set(prev);
      if (next.has(clauseId)) {
        next.delete(clauseId);
      } else {
        next.add(clauseId);
      }
      return next;
    });
  }

  const selectedTemplate = templates.find((t) => t.id === selectedTemplateId);
  const output = result?.output ?? null;

  return (
    <Dialog open={isOpen} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="size-5 text-teal-600 dark:text-teal-400" />
            Draft with AI
          </DialogTitle>
          <DialogDescription>
            {step === "SELECT_TEMPLATE" && "Select a template to generate a draft document."}
            {step === "LOADING" && "AI is analyzing matter data and preparing the draft..."}
            {step === "RESULTS" &&
              "Review the AI-generated draft below. Edit variables and select clauses before creating."}
            {step === "CONFIRMED" && "Review the gate below to approve or reject the draft."}
          </DialogDescription>
        </DialogHeader>

        {/* Error banner */}
        {error && (
          <Alert variant="warning">
            <AlertTriangle className="size-4" />
            <AlertTitle>Drafting Error</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {/* Step 1: Template Selection */}
        {step === "SELECT_TEMPLATE" && (
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-950 dark:text-slate-50">
                Document Template
              </label>
              {templates.length === 0 ? (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No document templates available. Create a template first.
                </p>
              ) : (
                <Select value={selectedTemplateId} onValueChange={setSelectedTemplateId}>
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Choose a template..." />
                  </SelectTrigger>
                  <SelectContent>
                    {templates
                      .filter((t) => t.active)
                      .map((template) => (
                        <SelectItem key={template.id} value={template.id}>
                          <div className="flex items-center gap-2">
                            <FileText className="size-3.5 text-slate-400" />
                            <span>{template.name}</span>
                          </div>
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              )}
              {selectedTemplate && selectedTemplate.description && (
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {selectedTemplate.description}
                </p>
              )}
            </div>
          </div>
        )}

        {/* Step 2: Loading */}
        {step === "LOADING" && (
          <div className="flex flex-col items-center justify-center gap-3 py-12">
            <Loader2 className="size-8 animate-spin text-teal-600 dark:text-teal-400" />
            <div className="text-center">
              <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
                Generating draft...
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                This typically takes 10-20 seconds.
              </p>
            </div>
            <Badge variant="neutral">AI</Badge>
          </div>
        )}

        {/* Step 3: Results */}
        {step === "RESULTS" && output && (
          <div className="space-y-6">
            {/* Warnings */}
            {output.warnings.length > 0 && (
              <div className="space-y-2">
                {output.warnings.map((warning, i) => (
                  <Alert key={i} variant="warning">
                    <AlertTriangle className="size-4" />
                    <AlertDescription>{warning}</AlertDescription>
                  </Alert>
                ))}
              </div>
            )}

            {/* Variable Fills */}
            <div className="space-y-2">
              <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
                Variable Fills
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Review and edit the AI-suggested values. Items needing attention are shown first.
              </p>
              <DraftingVariableTable variableFills={variableFills} onChange={setVariableFills} />
            </div>

            {/* Narrative Sections */}
            {output.narrativeSections.length > 0 && (
              <div className="space-y-2">
                <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
                  Narrative Sections
                </h3>
                <div className="space-y-1">
                  {output.narrativeSections.map((section) => {
                    const isExpanded = expandedSections.has(section.sectionName);
                    return (
                      <Collapsible
                        key={section.sectionName}
                        open={isExpanded}
                        onOpenChange={() => toggleSection(section.sectionName)}
                      >
                        <CollapsibleTrigger className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800">
                          {isExpanded ? (
                            <ChevronDown className="size-3.5" />
                          ) : (
                            <ChevronRight className="size-3.5" />
                          )}
                          {section.sectionName}
                        </CollapsibleTrigger>
                        <CollapsibleContent>
                          <div className="ml-6 space-y-2 rounded-md border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900">
                            <p className="text-sm leading-relaxed whitespace-pre-wrap text-slate-700 dark:text-slate-300">
                              {section.content}
                            </p>
                            {section.notes && (
                              <p className="text-xs text-slate-500 italic dark:text-slate-400">
                                Note: {section.notes}
                              </p>
                            )}
                          </div>
                        </CollapsibleContent>
                      </Collapsible>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Clause Recommendations */}
            {output.clauseRecommendations.length > 0 && (
              <div className="space-y-2">
                <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
                  Recommended Clauses
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Select clauses from your firm library to include in the draft.
                </p>
                <div className="space-y-2">
                  {output.clauseRecommendations.map((clause) => (
                    <label
                      key={clause.clauseId}
                      className="flex items-start gap-3 rounded-md border border-slate-200 p-3 transition-colors hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900"
                    >
                      <Checkbox
                        checked={selectedClauses.has(clause.clauseId)}
                        onCheckedChange={() => toggleClause(clause.clauseId)}
                        className="mt-0.5"
                      />
                      <div className="space-y-1">
                        <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                          {clause.clauseName}
                        </span>
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {clause.reasoning}
                        </p>
                      </div>
                    </label>
                  ))}
                </div>
              </div>
            )}

            {/* Recommended Actions */}
            {output.recommendedActions.length > 0 && (
              <div className="space-y-2">
                <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
                  Recommended Actions
                </h3>
                <ul className="list-inside list-disc space-y-1 text-sm text-slate-700 dark:text-slate-300">
                  {output.recommendedActions.map((action, i) => (
                    <li key={i}>
                      <span className="font-medium">{action.action}</span>
                      {" — "}
                      {action.reasoning}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Execution metadata */}
            {result && (
              <div className="flex flex-wrap items-center gap-4 text-xs text-slate-500 dark:text-slate-400">
                <span>Cost: {formatZarCents(result.costCents)}</span>
                {result.durationMs && <span>Completed in {result.durationMs}ms</span>}
                <Link
                  href={`/org/${slug}/settings/ai/history`}
                  className="text-teal-600 underline hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                >
                  View execution history
                </Link>
              </div>
            )}
          </div>
        )}

        {/* Step 4: Confirmed */}
        {step === "CONFIRMED" && result && (
          <div className="space-y-4">
            <div className="rounded-lg border border-teal-200 bg-teal-50 p-4 dark:border-teal-800 dark:bg-teal-950">
              <p className="text-sm font-medium text-teal-800 dark:text-teal-200">
                Draft document gate has been created. An authorized reviewer can approve the gate to
                generate the document.
              </p>
            </div>

            {/* Pending gate cards */}
            {canReviewGates &&
              result.gates
                .filter((gate) => gate.status === "PENDING")
                .map((gate) => (
                  <ExecutionGateCard
                    key={gate.id}
                    gate={gate}
                    onApprove={(gateId, notes) => approveGateAction(slug, gateId, notes)}
                    onReject={(gateId, notes) => rejectGateAction(slug, gateId, notes)}
                  />
                ))}
          </div>
        )}

        {/* Footer */}
        <DialogFooter>
          {step === "SELECT_TEMPLATE" && (
            <>
              <Button variant="outline" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
              <Button
                variant="accent"
                onClick={handleInvoke}
                disabled={!selectedTemplateId || templates.length === 0}
              >
                <Sparkles className="mr-1.5 size-3.5" />
                Generate Draft
              </Button>
            </>
          )}

          {step === "LOADING" && (
            <Button variant="outline" disabled>
              Cancel
            </Button>
          )}

          {step === "RESULTS" && (
            <>
              <Button variant="outline" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
              <Button variant="accent" onClick={handleConfirm}>
                <FileText className="mr-1.5 size-3.5" />
                Create Draft
              </Button>
            </>
          )}

          {step === "CONFIRMED" && (
            <Button variant="outline" onClick={() => handleOpenChange(false)}>
              Close
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
