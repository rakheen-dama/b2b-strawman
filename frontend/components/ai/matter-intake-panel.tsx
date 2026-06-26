"use client";

import { useState, useTransition } from "react";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { Button } from "@b2mash/ui/button";
import { Badge } from "@b2mash/ui/badge";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { Loader2, Sparkles } from "lucide-react";
import { IntakeResultDisplay } from "@/components/ai/intake-result-display";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import { invokeMatterIntakeAction } from "@/app/(app)/org/[slug]/projects/new/intake-actions";
import { approveGateAction, rejectGateAction } from "@/app/(app)/org/[slug]/ai/reviews/actions";
import type { MatterIntakeResponse } from "@/lib/api/ai";
import Link from "next/link";

interface MatterIntakePanelProps {
  customerId: string;
  slug: string;
  description: string;
  isAiConfigured: boolean;
  canReviewGates: boolean;
  onTemplateSelected?: (templateId: string) => void;
}

type PanelState =
  | { phase: "IDLE" }
  | { phase: "LOADING" }
  | { phase: "SUCCESS"; result: MatterIntakeResponse }
  | { phase: "ERROR"; message: string };

function formatZarCents(cents: number): string {
  return `R ${(cents / 100).toFixed(2)}`;
}

function getDisabledReason(
  props: Pick<MatterIntakePanelProps, "isAiConfigured" | "customerId" | "description">
): string | null {
  if (!props.isAiConfigured) {
    return "Connect an Anthropic API key in Settings > AI to use this feature.";
  }
  if (!props.customerId) {
    return "Select a customer before running AI intake.";
  }
  if (props.description.trim().length < 20) {
    return "Enter at least 20 characters in the description.";
  }
  return null;
}

export function MatterIntakePanel({
  customerId,
  slug,
  description,
  isAiConfigured,
  canReviewGates,
  onTemplateSelected,
}: MatterIntakePanelProps) {
  const [state, setState] = useState<PanelState>({ phase: "IDLE" });
  const [, startTransition] = useTransition();

  const disabledReason = getDisabledReason({
    customerId,
    description,
    isAiConfigured,
  });
  const prerequisitesMet = disabledReason === null;

  function handleInvoke() {
    setState({ phase: "LOADING" });
    startTransition(async () => {
      try {
        const result = await invokeMatterIntakeAction(slug, customerId, description);
        if (result.success && result.data) {
          setState({ phase: "SUCCESS", result: result.data });
        } else {
          setState({ phase: "ERROR", message: result.error ?? "Intake analysis failed." });
        }
      } catch {
        setState({
          phase: "ERROR",
          message: "Intake analysis failed unexpectedly. Please try again.",
        });
      }
    });
  }

  function handleReset() {
    setState({ phase: "IDLE" });
  }

  function handleApplyTemplate(templateId: string) {
    onTemplateSelected?.(templateId);
  }

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Sparkles className="size-4 text-teal-600 dark:text-teal-400" />
          <CardTitle className="text-base font-semibold text-slate-950 dark:text-slate-50">
            AI Matter Intake
          </CardTitle>
          <Badge variant="neutral">AI</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {state.phase === "IDLE" && (
          <>
            {prerequisitesMet ? (
              <Button variant="accent" size="sm" onClick={handleInvoke}>
                <Sparkles className="mr-1.5 size-3.5" />
                Get AI Recommendations
              </Button>
            ) : (
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span tabIndex={0}>
                      <Button variant="accent" size="sm" disabled>
                        <Sparkles className="mr-1.5 size-3.5" />
                        Get AI Recommendations
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
            <span>Analysing matter...</span>
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
            <IntakeResultDisplay
              output={state.result.output}
              onApplyTemplate={handleApplyTemplate}
            />

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
                    slug={slug}
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
