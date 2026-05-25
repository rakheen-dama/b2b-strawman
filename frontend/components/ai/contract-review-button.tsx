"use client";

import { useState, useTransition } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Loader2, Sparkles } from "lucide-react";
import { ContractReviewResults } from "@/components/ai/contract-review-results";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import { invokeContractReviewAction } from "@/app/(app)/org/[slug]/projects/[id]/ai-review-actions";
import { approveGateAction, rejectGateAction } from "@/app/(app)/org/[slug]/ai/reviews/actions";
import type { ContractReviewResponse } from "@/lib/api/ai";
import Link from "next/link";

interface ContractReviewButtonProps {
  documentId: string;
  projectId: string;
  slug: string;
  isAiConfigured: boolean;
  canExecuteAi: boolean;
  canReviewGates: boolean;
}

const REVIEWABLE_CONTENT_TYPES = new Set([
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
]);

export function isReviewableDocument(contentType: string, status: string): boolean {
  return status === "UPLOADED" && REVIEWABLE_CONTENT_TYPES.has(contentType);
}

type PanelState =
  | { phase: "IDLE" }
  | { phase: "LOADING" }
  | { phase: "SUCCESS"; result: ContractReviewResponse }
  | { phase: "ERROR"; message: string };

function formatZarCents(cents: number): string {
  return `R ${(cents / 100).toFixed(2)}`;
}

function getDisabledReason(
  props: Pick<ContractReviewButtonProps, "isAiConfigured" | "canExecuteAi">
): string | null {
  if (!props.isAiConfigured) {
    return "Connect an Anthropic API key in Settings > AI to use this feature.";
  }
  if (!props.canExecuteAi) {
    return "You do not have permission to invoke AI skills.";
  }
  return null;
}

export function ContractReviewButton({
  documentId,
  projectId,
  slug,
  isAiConfigured,
  canExecuteAi,
  canReviewGates,
}: ContractReviewButtonProps) {
  const [state, setState] = useState<PanelState>({ phase: "IDLE" });
  const [, startTransition] = useTransition();

  const disabledReason = getDisabledReason({ isAiConfigured, canExecuteAi });
  const prerequisitesMet = disabledReason === null;

  function handleReview() {
    setState({ phase: "LOADING" });
    startTransition(async () => {
      try {
        const result = await invokeContractReviewAction(slug, projectId, documentId);
        if (result.success && result.data) {
          setState({ phase: "SUCCESS", result: result.data });
        } else {
          setState({ phase: "ERROR", message: result.error ?? "Contract review failed." });
        }
      } catch {
        setState({
          phase: "ERROR",
          message: "Contract review failed unexpectedly. Please try again.",
        });
      }
    });
  }

  function handleReset() {
    setState({ phase: "IDLE" });
  }

  return (
    <div className="space-y-4">
      {state.phase === "IDLE" && (
        <>
          {prerequisitesMet ? (
            <Button variant="accent" size="sm" onClick={handleReview}>
              <Sparkles className="mr-1.5 size-3.5" />
              Review with AI
            </Button>
          ) : (
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <span tabIndex={0}>
                    <Button variant="accent" size="sm" disabled>
                      <Sparkles className="mr-1.5 size-3.5" />
                      Review with AI
                    </Button>
                  </span>
                </TooltipTrigger>
                <TooltipContent>
                  <p>{disabledReason}</p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          )}
        </>
      )}

      {state.phase === "LOADING" && (
        <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
          <Loader2 className="size-4 animate-spin" />
          <span>Reviewing contract...</span>
          <Badge variant="neutral">AI</Badge>
        </div>
      )}

      {state.phase === "ERROR" && (
        <div className="space-y-3">
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
            {state.message}
          </div>
          <Button variant="outline" size="sm" onClick={handleReset}>
            Try Again
          </Button>
        </div>
      )}

      {state.phase === "SUCCESS" && (
        <div className="space-y-4">
          <ContractReviewResults output={state.result.output} />

          {/* Execution metadata */}
          <div className="flex flex-wrap items-center gap-4 text-xs text-slate-500 dark:text-slate-400">
            <span>Cost: {formatZarCents(state.result.costCents)}</span>
            <span>Completed in {state.result.durationMs}ms</span>
            <Link
              href={`/org/${slug}/settings/ai/history`}
              className="text-teal-600 underline hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
            >
              View execution history
            </Link>
          </div>

          {/* Pending gate cards */}
          {canReviewGates &&
            state.result.gates
              .filter((gate) => gate.status === "PENDING")
              .map((gate) => (
                <ExecutionGateCard
                  key={gate.id}
                  gate={gate}
                  onApprove={(gateId, notes) => approveGateAction(slug, gateId, notes)}
                  onReject={(gateId, notes) => rejectGateAction(slug, gateId, notes)}
                />
              ))}

          <Button variant="outline" size="sm" onClick={handleReset}>
            Run Again
          </Button>
        </div>
      )}
    </div>
  );
}
