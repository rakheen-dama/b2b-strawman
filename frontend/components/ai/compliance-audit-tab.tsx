"use client";

import { useState, useTransition } from "react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { ChevronDown, ChevronRight, Loader2, Sparkles } from "lucide-react";
import {
  ComplianceAuditSummary,
  getGradeBadgeVariant,
} from "@/components/ai/compliance-audit-summary";
import { ComplianceFindingList } from "@/components/ai/compliance-finding-list";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import {
  invokeComplianceAuditAction,
  fetchAuditReportsAction,
} from "@/app/(app)/org/[slug]/compliance/actions";
import { approveGateAction, rejectGateAction } from "@/app/(app)/org/[slug]/ai/reviews/actions";
import type {
  ComplianceAuditReportResponse,
  ComplianceAuditInvokeResponse,
  PaginatedResponse,
} from "@/lib/api/compliance-audit";

interface ComplianceAuditTabProps {
  slug: string;
  isAiConfigured: boolean;
  canExecuteAi: boolean;
  canReviewGates: boolean;
  initialReports: PaginatedResponse<ComplianceAuditReportResponse>;
}

type TabState =
  | { phase: "IDLE" }
  | { phase: "LOADING" }
  | { phase: "SUCCESS"; result: ComplianceAuditInvokeResponse }
  | { phase: "ERROR"; message: string };

function getDisabledReason(isAiConfigured: boolean, canExecuteAi: boolean): string | null {
  if (!isAiConfigured) {
    return "Connect an Anthropic API key in Settings > AI to use this feature.";
  }
  if (!canExecuteAi) {
    return "You do not have permission to invoke AI skills.";
  }
  return null;
}

function formatTotalFindings(report: ComplianceAuditReportResponse): string {
  if (!report.findingCounts) return "";
  const total =
    report.findingCounts.critical +
    report.findingCounts.high +
    report.findingCounts.medium +
    report.findingCounts.low +
    report.findingCounts.info;
  return `${total} finding${total !== 1 ? "s" : ""}`;
}

export function ComplianceAuditTab({
  slug,
  isAiConfigured,
  canExecuteAi,
  canReviewGates,
  initialReports,
}: ComplianceAuditTabProps) {
  const [state, setState] = useState<TabState>({ phase: "IDLE" });
  const [reports, setReports] =
    useState<PaginatedResponse<ComplianceAuditReportResponse>>(initialReports);
  const [expandedReportId, setExpandedReportId] = useState<string | null>(null);
  const [, startTransition] = useTransition();

  const disabledReason = getDisabledReason(isAiConfigured, canExecuteAi);
  const prerequisitesMet = disabledReason === null;

  const latestReport = reports.content.length > 0 ? reports.content[0] : null;
  const historyReports = reports.content.length > 1 ? reports.content.slice(1) : [];

  function handleRunAudit() {
    setState({ phase: "LOADING" });
    startTransition(async () => {
      try {
        const result = await invokeComplianceAuditAction(slug);
        if (result.success && result.data) {
          setState({ phase: "SUCCESS", result: result.data });
          // Refresh reports list
          const updated = await fetchAuditReportsAction(slug);
          if (updated.success && updated.data) {
            setReports(updated.data);
          }
        } else {
          setState({
            phase: "ERROR",
            message: result.error ?? "Compliance audit failed.",
          });
        }
      } catch {
        setState({
          phase: "ERROR",
          message: "Compliance audit failed unexpectedly. Please try again.",
        });
      }
    });
  }

  function handleReset() {
    setState({ phase: "IDLE" });
  }

  function toggleExpanded(reportId: string) {
    setExpandedReportId((prev) => (prev === reportId ? null : reportId));
  }

  return (
    <div className="space-y-6">
      {/* Header with Run Button */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            AI Compliance Audit
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Run an AI-powered compliance audit to assess your regulatory posture.
          </p>
        </div>
        {state.phase !== "LOADING" && (
          <>
            {prerequisitesMet ? (
              <Button variant="accent" size="sm" onClick={handleRunAudit}>
                <Sparkles className="mr-1.5 size-3.5" />
                Run AI Audit
              </Button>
            ) : (
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span tabIndex={0}>
                      <Button variant="accent" size="sm" disabled>
                        <Sparkles className="mr-1.5 size-3.5" />
                        Run AI Audit
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
      </div>

      {/* Loading State */}
      {state.phase === "LOADING" && (
        <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
          <Loader2 className="size-4 animate-spin" />
          <span>Running compliance audit... This may take 30-60 seconds.</span>
          <Badge variant="neutral">AI</Badge>
        </div>
      )}

      {/* Error State */}
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

      {/* Success State — show gate cards if any */}
      {state.phase === "SUCCESS" && (
        <div className="space-y-4">
          <div className="rounded-lg border border-teal-200 bg-teal-50 p-3 text-sm text-teal-800 dark:border-teal-800 dark:bg-teal-950 dark:text-teal-200">
            Audit completed successfully.
            {state.result.output && <span> Grade: {state.result.output.overallGrade}</span>}
          </div>

          {/* Execution metadata */}
          <div className="flex flex-wrap items-center gap-4 text-xs text-slate-500 dark:text-slate-400">
            <span>Cost: R {(state.result.costCents / 100).toFixed(2)}</span>
            <span>Completed in {state.result.durationMs}ms</span>
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
            Dismiss
          </Button>
        </div>
      )}

      {/* Latest Audit Summary */}
      {latestReport ? (
        <div className="space-y-4">
          <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">Latest Audit</h3>
          <ComplianceAuditSummary report={latestReport} />

          {/* Finding List for latest report */}
          <ComplianceFindingList
            reportId={latestReport.id}
            slug={slug}
            canReview={canReviewGates}
          />
        </div>
      ) : (
        state.phase === "IDLE" && (
          <Card className="border-slate-200 dark:border-slate-800">
            <CardContent className="py-8">
              <p className="text-center text-sm text-slate-500 dark:text-slate-400">
                No audits yet. Run an AI audit to assess your compliance posture.
              </p>
            </CardContent>
          </Card>
        )
      )}

      {/* Audit History */}
      {historyReports.length > 0 && (
        <div className="space-y-3">
          <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">Audit History</h3>
          {historyReports.map((report) => (
            <Card key={report.id} className="border-slate-200 dark:border-slate-800">
              <CardHeader className="pb-2">
                <button
                  type="button"
                  className="flex w-full items-center justify-between text-left"
                  onClick={() => toggleExpanded(report.id)}
                >
                  <div className="flex items-center gap-3">
                    {expandedReportId === report.id ? (
                      <ChevronDown className="size-4 text-slate-400" />
                    ) : (
                      <ChevronRight className="size-4 text-slate-400" />
                    )}
                    <div>
                      <CardTitle className="text-sm font-medium text-slate-950 dark:text-slate-50">
                        {report.publishedAt
                          ? new Date(report.publishedAt).toLocaleDateString("en-ZA", {
                              year: "numeric",
                              month: "short",
                              day: "numeric",
                            })
                          : "Draft"}
                      </CardTitle>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {formatTotalFindings(report)}
                      </p>
                    </div>
                  </div>
                  <Badge variant={getGradeBadgeVariant(report.overallGrade)}>
                    {report.overallGrade}
                  </Badge>
                </button>
              </CardHeader>
              {expandedReportId === report.id && (
                <CardContent className="pt-0">
                  <ComplianceAuditSummary report={report} />
                  <div className="mt-4">
                    <ComplianceFindingList
                      reportId={report.id}
                      slug={slug}
                      canReview={canReviewGates}
                    />
                  </div>
                </CardContent>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
