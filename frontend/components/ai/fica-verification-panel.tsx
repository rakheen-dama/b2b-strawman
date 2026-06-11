"use client";

import { useState, useTransition } from "react";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { Button } from "@b2mash/ui/button";
import { Badge } from "@b2mash/ui/badge";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { Loader2, Sparkles } from "lucide-react";
import { FicaResultDisplay } from "@/components/ai/fica-result-display";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import { invokeFicaVerificationAction } from "@/app/(app)/org/[slug]/customers/[id]/fica-actions";
import { approveGateAction, rejectGateAction } from "@/app/(app)/org/[slug]/ai/reviews/actions";
import type { FicaVerificationResponse } from "@/lib/api/ai";
import Link from "next/link";

interface FicaVerificationPanelProps {
  customerId: string;
  slug: string;
  hasDocuments: boolean;
  hasPendingChecklistItems: boolean;
  isAiConfigured: boolean;
  canReviewGates: boolean;
}

type PanelState =
  | { phase: "IDLE" }
  | { phase: "LOADING" }
  | { phase: "SUCCESS"; result: FicaVerificationResponse }
  | { phase: "ERROR"; message: string };

function formatZarCents(cents: number): string {
  return `R ${(cents / 100).toFixed(2)}`;
}

function getDisabledReason(
  props: Pick<
    FicaVerificationPanelProps,
    "isAiConfigured" | "hasDocuments" | "hasPendingChecklistItems"
  >
): string | null {
  if (!props.isAiConfigured) {
    return "Connect an Anthropic API key in Settings > AI to use this feature.";
  }
  if (!props.hasDocuments) {
    return "Upload at least one document for this customer before running AI verification.";
  }
  if (!props.hasPendingChecklistItems) {
    return "No pending checklist items to verify.";
  }
  return null;
}

export function FicaVerificationPanel({
  customerId,
  slug,
  hasDocuments,
  hasPendingChecklistItems,
  isAiConfigured,
  canReviewGates,
}: FicaVerificationPanelProps) {
  const [state, setState] = useState<PanelState>({ phase: "IDLE" });
  const [, startTransition] = useTransition();

  const disabledReason = getDisabledReason({
    hasDocuments,
    hasPendingChecklistItems,
    isAiConfigured,
  });
  const prerequisitesMet = disabledReason === null;

  function handleVerify() {
    setState({ phase: "LOADING" });
    startTransition(async () => {
      try {
        const result = await invokeFicaVerificationAction(slug, customerId);
        if (result.success && result.data) {
          setState({ phase: "SUCCESS", result: result.data });
        } else {
          setState({ phase: "ERROR", message: result.error ?? "Verification failed." });
        }
      } catch {
        setState({
          phase: "ERROR",
          message: "Verification failed unexpectedly. Please try again.",
        });
      }
    });
  }

  function handleReset() {
    setState({ phase: "IDLE" });
  }

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Sparkles className="size-4 text-teal-600 dark:text-teal-400" />
          <CardTitle className="text-base font-semibold text-slate-950 dark:text-slate-50">
            FICA Verification
          </CardTitle>
          <Badge variant="neutral">AI</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {state.phase === "IDLE" && (
          <>
            {prerequisitesMet ? (
              <Button variant="accent" size="sm" onClick={handleVerify}>
                <Sparkles className="mr-1.5 size-3.5" />
                Verify with AI
              </Button>
            ) : (
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span tabIndex={0}>
                      <Button variant="accent" size="sm" disabled>
                        <Sparkles className="mr-1.5 size-3.5" />
                        Verify with AI
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
            <span>Verifying...</span>
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
            <FicaResultDisplay output={state.result.output} />

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

            {/* Pending gate cards — only show approve/reject to users with AI_REVIEW */}
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
      </CardContent>
    </Card>
  );
}
