"use client";

import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { FileText } from "lucide-react";
import type { MatterIntakeOutput } from "@/lib/api/ai";

interface IntakeResultDisplayProps {
  output: MatterIntakeOutput | null;
  onApplyTemplate?: (templateId: string) => void;
}

function formatZarCents(cents: number): string {
  return new Intl.NumberFormat("en-ZA", {
    style: "currency",
    currency: "ZAR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(cents / 100);
}

function getPriorityBadgeVariant(priority: "HIGH" | "MEDIUM" | "LOW") {
  switch (priority) {
    case "HIGH":
      return "destructive" as const;
    case "MEDIUM":
      return "warning" as const;
    case "LOW":
      return "neutral" as const;
  }
}

function getConflictStatusBadgeVariant(
  status: "CLEAR" | "POTENTIAL_CONFLICT" | "CONFLICT_DETECTED"
) {
  switch (status) {
    case "CLEAR":
      return "success" as const;
    case "POTENTIAL_CONFLICT":
      return "warning" as const;
    case "CONFLICT_DETECTED":
      return "destructive" as const;
  }
}

function formatConflictStatus(status: "CLEAR" | "POTENTIAL_CONFLICT" | "CONFLICT_DETECTED") {
  switch (status) {
    case "CLEAR":
      return "Clear";
    case "POTENTIAL_CONFLICT":
      return "Potential Conflict";
    case "CONFLICT_DETECTED":
      return "Conflict Detected";
  }
}

export function IntakeResultDisplay({ output, onApplyTemplate }: IntakeResultDisplayProps) {
  if (!output) {
    return (
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="py-6">
          <p className="text-center text-sm text-slate-500 dark:text-slate-400">
            No intake output available.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {/* Matter Classification */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
            Matter Classification
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <div className="flex items-center gap-3">
            <Badge variant="neutral">{output.matterClassification.recommendedType}</Badge>
            <span className="font-mono text-xs text-slate-500 tabular-nums dark:text-slate-400">
              {Math.round(output.matterClassification.confidence * 100)}% confidence
            </span>
          </div>
          <p className="text-sm text-slate-700 dark:text-slate-300">
            {output.matterClassification.reasoning}
          </p>
        </CardContent>
      </Card>

      {/* Template Recommendation */}
      {output.templateRecommendation && (
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
              Recommended Template
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center gap-2">
              <FileText className="size-4 text-teal-600 dark:text-teal-400" />
              <span className="font-medium text-slate-900 dark:text-slate-100">
                {output.templateRecommendation.templateName}
              </span>
            </div>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              {output.templateRecommendation.reasoning}
            </p>
            {output.templateRecommendation.customisationNotes && (
              <p className="text-sm text-slate-500 italic dark:text-slate-400">
                {output.templateRecommendation.customisationNotes}
              </p>
            )}
            {onApplyTemplate && (
              <Button
                variant="accent"
                size="sm"
                onClick={() => onApplyTemplate(output.templateRecommendation!.templateId)}
              >
                Apply Template
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      {/* Required Documents */}
      {output.requiredDocuments.length > 0 && (
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
              Required Documents
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-2">
              {output.requiredDocuments.map((doc) => (
                <li key={doc.documentType} className="flex items-start gap-2">
                  <Badge
                    variant={getPriorityBadgeVariant(doc.priority)}
                    className="mt-0.5 shrink-0"
                  >
                    {doc.priority}
                  </Badge>
                  <div>
                    <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                      {doc.documentType}
                    </p>
                    <p className="text-sm text-slate-600 dark:text-slate-400">{doc.reasoning}</p>
                  </div>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {/* Fee Estimate */}
      {output.feeEstimate && (
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
              Fee Estimate
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <div className="flex items-center gap-2">
              <span className="font-mono text-sm font-semibold text-slate-900 tabular-nums dark:text-slate-100">
                {formatZarCents(output.feeEstimate.estimatedRangeMinCents)} &ndash;{" "}
                {formatZarCents(output.feeEstimate.estimatedRangeMaxCents)}
              </span>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Basis: {output.feeEstimate.tariffBasis}
            </p>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              {output.feeEstimate.reasoning}
            </p>
            {output.feeEstimate.assumptions.length > 0 && (
              <div>
                <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  Assumptions
                </p>
                <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm text-slate-600 dark:text-slate-400">
                  {output.feeEstimate.assumptions.map((assumption, i) => (
                    <li key={`assumption-${i}`}>{assumption}</li>
                  ))}
                </ul>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Conflict Screening */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
            Conflict Screening
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <Badge variant={getConflictStatusBadgeVariant(output.conflictScreening.status)}>
            {formatConflictStatus(output.conflictScreening.status)}
          </Badge>
          {output.conflictScreening.matches.length > 0 && (
            <div className="space-y-2">
              {output.conflictScreening.matches.map((match) => (
                <div
                  key={`${match.existingMatterName}-${match.customerName}-${match.matchType}`}
                  className="rounded-md border border-slate-200 p-3 dark:border-slate-700"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                      {match.existingMatterName}
                    </span>
                    <Badge variant="neutral">{match.matchType.replaceAll("_", " ")}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                    Customer: {match.customerName}
                  </p>
                  <p className="mt-1 text-sm text-slate-700 dark:text-slate-300">
                    {match.reasoning}
                  </p>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Risk Flags */}
      {output.riskFlags.length > 0 && (
        <div className="space-y-2">
          {output.riskFlags.map((flag, i) => (
            <div
              key={`risk-flag-${i}`}
              className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200"
            >
              {flag}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
