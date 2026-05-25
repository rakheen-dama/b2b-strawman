"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { ChevronDown, ChevronRight, AlertTriangle, Shield, Scale } from "lucide-react";
import type { ContractReviewOutput, ContractReviewFinding } from "@/lib/api/ai";

interface ContractReviewResultsProps {
  output: ContractReviewOutput | null;
}

function getRiskBadgeVariant(risk: string) {
  const upper = risk.toUpperCase();
  if (upper.includes("HIGH")) return "destructive" as const;
  if (upper.includes("MEDIUM")) return "warning" as const;
  if (upper.includes("LOW")) return "success" as const;
  return "neutral" as const;
}

function getSeverityBadgeVariant(severity: ContractReviewFinding["severity"]) {
  switch (severity) {
    case "HIGH":
      return "destructive" as const;
    case "MEDIUM":
      return "warning" as const;
    case "LOW":
      return "success" as const;
    default:
      return "neutral" as const;
  }
}

function groupFindingsBySeverity(
  findings: ContractReviewFinding[]
): Record<string, ContractReviewFinding[]> {
  const groups: Record<string, ContractReviewFinding[]> = {};
  const order: ContractReviewFinding["severity"][] = ["HIGH", "MEDIUM", "LOW"];

  for (const severity of order) {
    const matched = findings.filter((f) => f.severity === severity);
    if (matched.length > 0) {
      groups[severity] = matched;
    }
  }

  return groups;
}

function FindingCard({ finding }: { finding: ContractReviewFinding }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="rounded-lg border border-slate-200 dark:border-slate-700">
      <button
        type="button"
        className="flex w-full items-center gap-2 px-4 py-3 text-left"
        aria-expanded={expanded}
        onClick={() => setExpanded(!expanded)}
      >
        {expanded ? (
          <ChevronDown className="size-3.5 shrink-0 text-slate-400" />
        ) : (
          <ChevronRight className="size-3.5 shrink-0 text-slate-400" />
        )}
        <Badge variant={getSeverityBadgeVariant(finding.severity)} className="shrink-0">
          {finding.severity}
        </Badge>
        <span className="min-w-0 flex-1 truncate text-sm font-medium text-slate-950 dark:text-slate-50">
          {finding.title}
        </span>
        {finding.clauseReference && (
          <span className="shrink-0 font-mono text-xs text-slate-500 dark:text-slate-400">
            {finding.clauseReference}
          </span>
        )}
      </button>
      {expanded && (
        <div className="space-y-3 border-t border-slate-200 px-4 py-3 dark:border-slate-700">
          <div>
            <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
              Description
            </p>
            <p className="mt-1 text-sm leading-relaxed text-slate-700 dark:text-slate-300">
              {finding.description}
            </p>
          </div>
          {finding.riskExplanation && (
            <div>
              <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                Risk
              </p>
              <p className="mt-1 text-sm leading-relaxed text-slate-700 dark:text-slate-300">
                {finding.riskExplanation}
              </p>
            </div>
          )}
          {finding.recommendation && (
            <div>
              <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                Recommendation
              </p>
              <p className="mt-1 text-sm leading-relaxed text-slate-700 dark:text-slate-300">
                {finding.recommendation}
              </p>
            </div>
          )}
          {finding.statutoryReference && (
            <div>
              <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                Legal Basis
              </p>
              <p className="mt-1 text-sm leading-relaxed text-slate-700 dark:text-slate-300">
                {finding.statutoryReference}
              </p>
            </div>
          )}
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500 dark:text-slate-400">Category:</span>
            <Badge variant="neutral">{finding.category}</Badge>
          </div>
        </div>
      )}
    </div>
  );
}

export function ContractReviewResults({ output }: ContractReviewResultsProps) {
  if (!output) {
    return (
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="py-6">
          <p className="text-center text-sm text-slate-500 dark:text-slate-400">
            No review output available.
          </p>
        </CardContent>
      </Card>
    );
  }

  const groupedFindings = groupFindingsBySeverity(output.findings);
  const highCount = output.findings.filter((f) => f.severity === "HIGH").length;
  const mediumCount = output.findings.filter((f) => f.severity === "MEDIUM").length;
  const lowCount = output.findings.filter((f) => f.severity === "LOW").length;

  return (
    <div className="space-y-4">
      {/* Risk Assessment & Finding Counts */}
      <div className="flex flex-wrap items-center gap-4">
        <div className="space-y-1">
          <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Overall Risk
          </p>
          <Badge variant={getRiskBadgeVariant(output.overallRiskAssessment)}>
            {output.overallRiskAssessment}
          </Badge>
        </div>
        <div className="space-y-1">
          <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Findings
          </p>
          <div className="flex items-center gap-1.5">
            {highCount > 0 && <Badge variant="destructive">{highCount} High</Badge>}
            {mediumCount > 0 && <Badge variant="warning">{mediumCount} Medium</Badge>}
            {lowCount > 0 && <Badge variant="success">{lowCount} Low</Badge>}
            {output.findings.length === 0 && (
              <span className="text-sm text-slate-500 dark:text-slate-400">None</span>
            )}
          </div>
        </div>
      </div>

      {/* Document Classification */}
      {output.documentClassification && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Scale className="size-3.5 text-slate-400" />
          <span className="text-slate-600 dark:text-slate-400">
            {output.documentClassification.type}
            {output.documentClassification.subtype && ` — ${output.documentClassification.subtype}`}
          </span>
          {output.documentClassification.partiesIdentified.length > 0 && (
            <span className="text-slate-500 dark:text-slate-400">
              ({output.documentClassification.partiesIdentified.join(", ")})
            </span>
          )}
        </div>
      )}

      {/* Executive Summary */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
            Executive Summary
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm leading-relaxed text-slate-700 dark:text-slate-300">
            {output.executiveSummary}
          </p>
        </CardContent>
      </Card>

      {/* Findings grouped by severity */}
      {Object.entries(groupedFindings).map(([severity, findings]) => (
        <div key={severity} className="space-y-2">
          <h4 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
            {severity} Risk Findings ({findings.length})
          </h4>
          {findings.map((finding, i) => (
            <FindingCard key={`${finding.title}-${i}`} finding={finding} />
          ))}
        </div>
      ))}

      {/* Missing Protections */}
      {output.missingProtections.length > 0 && (
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-3">
            <div className="flex items-center gap-2">
              <Shield className="size-4 text-amber-500" />
              <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
                Missing Protections ({output.missingProtections.length})
              </CardTitle>
            </div>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {output.missingProtections.map((mp, i) => (
                <li key={`${mp.protection}-${i}`} className="space-y-1">
                  <div className="flex items-center gap-2">
                    <AlertTriangle className="size-3.5 text-amber-500" />
                    <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                      {mp.protection}
                    </span>
                    {mp.priority && (
                      <Badge variant={getRiskBadgeVariant(mp.priority)}>{mp.priority}</Badge>
                    )}
                  </div>
                  <p className="pl-6 text-sm text-slate-700 dark:text-slate-300">{mp.reasoning}</p>
                  {mp.recommendation && (
                    <p className="pl-6 text-sm text-slate-500 italic dark:text-slate-400">
                      {mp.recommendation}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {/* Recommended Actions */}
      {output.recommendedActions.length > 0 && (
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
              Recommended Actions
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {output.recommendedActions.map((action, i) => (
                <li key={`${action.action}-${i}`} className="space-y-1">
                  <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
                    {action.action}
                  </p>
                  <p className="text-sm text-slate-700 dark:text-slate-300">{action.reasoning}</p>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
