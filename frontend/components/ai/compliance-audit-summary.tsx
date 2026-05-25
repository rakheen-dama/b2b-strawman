"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { ComplianceAuditReportResponse, CategoryScore } from "@/lib/api/compliance-audit";

interface ComplianceAuditSummaryProps {
  report: ComplianceAuditReportResponse;
}

function getGradeBadgeVariant(grade: string): "success" | "warning" | "destructive" | "neutral" {
  const letter = grade.replace(/[+-]/g, "");
  if (letter === "A" || letter === "B") return "success";
  if (letter === "C") return "warning";
  if (letter === "D" || letter === "F") return "destructive";
  return "neutral";
}

function getSeverityBadgeVariant(
  severity: string
): "destructive" | "warning" | "success" | "neutral" {
  switch (severity.toUpperCase()) {
    case "CRITICAL":
      return "destructive";
    case "HIGH":
      return "destructive";
    case "MEDIUM":
      return "warning";
    case "LOW":
      return "success";
    case "INFO":
      return "neutral";
    default:
      return "neutral";
  }
}

function formatCategoryName(key: string): string {
  return key
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function ComplianceAuditSummary({ report }: ComplianceAuditSummaryProps) {
  const categoryEntries = Object.entries(report.categoryScores as Record<string, CategoryScore>);

  return (
    <div className="space-y-4">
      {/* Overall Grade & Assessment */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between gap-4">
            <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
              Overall Assessment
            </CardTitle>
            <Badge
              variant={getGradeBadgeVariant(report.overallGrade)}
              className="px-3 py-1 text-lg"
            >
              {report.overallGrade}
            </Badge>
          </div>
        </CardHeader>
        <CardContent>
          <p className="text-sm leading-relaxed text-slate-700 dark:text-slate-300">
            {report.overallAssessment}
          </p>
          {report.publishedAt && (
            <p className="mt-3 font-mono text-xs text-slate-400 tabular-nums dark:text-slate-500">
              Published{" "}
              {new Date(report.publishedAt).toLocaleDateString("en-ZA", {
                year: "numeric",
                month: "short",
                day: "numeric",
              })}
              {report.publishedBy && ` by ${report.publishedBy}`}
            </p>
          )}
        </CardContent>
      </Card>

      {/* Category Scores */}
      {categoryEntries.length > 0 && (
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
              Category Scores
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Category</TableHead>
                  <TableHead>Grade</TableHead>
                  <TableHead className="text-right">Compliant</TableHead>
                  <TableHead className="text-right">Non-Compliant</TableHead>
                  <TableHead className="text-right">Critical</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {categoryEntries.map(([key, score]) => (
                  <TableRow key={key}>
                    <TableCell className="font-medium">{formatCategoryName(key)}</TableCell>
                    <TableCell>
                      <Badge variant={getGradeBadgeVariant(score.grade)}>{score.grade}</Badge>
                    </TableCell>
                    <TableCell className="text-right">{score.compliant}</TableCell>
                    <TableCell className="text-right">{score.nonCompliant}</TableCell>
                    <TableCell className="text-right">
                      {score.critical > 0 ? (
                        <span className="font-semibold text-red-600 dark:text-red-400">
                          {score.critical}
                        </span>
                      ) : (
                        score.critical
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* Finding Count Badges */}
      {report.findingCounts && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Findings:
          </span>
          {report.findingCounts.critical > 0 && (
            <Badge variant={getSeverityBadgeVariant("CRITICAL")}>
              Critical: {report.findingCounts.critical}
            </Badge>
          )}
          {report.findingCounts.high > 0 && (
            <Badge variant={getSeverityBadgeVariant("HIGH")}>
              High: {report.findingCounts.high}
            </Badge>
          )}
          {report.findingCounts.medium > 0 && (
            <Badge variant={getSeverityBadgeVariant("MEDIUM")}>
              Medium: {report.findingCounts.medium}
            </Badge>
          )}
          {report.findingCounts.low > 0 && (
            <Badge variant={getSeverityBadgeVariant("LOW")}>Low: {report.findingCounts.low}</Badge>
          )}
          {report.findingCounts.info > 0 && (
            <Badge variant={getSeverityBadgeVariant("INFO")}>
              Info: {report.findingCounts.info}
            </Badge>
          )}
          {report.findingCounts.critical === 0 &&
            report.findingCounts.high === 0 &&
            report.findingCounts.medium === 0 &&
            report.findingCounts.low === 0 &&
            report.findingCounts.info === 0 && (
              <span className="text-xs text-slate-500 dark:text-slate-400">No findings</span>
            )}
        </div>
      )}
    </div>
  );
}
