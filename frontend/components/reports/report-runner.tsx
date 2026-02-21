"use client";

import { useState, useCallback } from "react";
import { Button } from "@/components/ui/button";
import { Download, FileText } from "lucide-react";
import { ReportParameterForm } from "@/components/reports/report-parameter-form";
import { ReportResults } from "@/components/reports/report-results";
import {
  executeReportAction,
  exportReportCsvAction,
  exportReportPdfAction,
} from "@/app/(app)/org/[slug]/reports/[reportSlug]/actions";
import type {
  ReportDefinitionDetail,
  ReportExecutionResponse,
} from "@/lib/api/reports";

interface ReportRunnerProps {
  definition: ReportDefinitionDetail;
}

const PAGE_SIZE = 25;

export function ReportRunner({ definition }: ReportRunnerProps) {
  const [response, setResponse] = useState<ReportExecutionResponse | null>(
    null,
  );
  const [isLoading, setIsLoading] = useState(false);
  const [currentParams, setCurrentParams] = useState<Record<
    string,
    unknown
  > | null>(null);
  const [hasRun, setHasRun] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runReport = useCallback(
    async (parameters: Record<string, unknown>, page: number) => {
      setIsLoading(true);
      setError(null);

      const result = await executeReportAction(
        definition.slug,
        parameters,
        page,
        PAGE_SIZE,
      );

      if (result.data) {
        setResponse(result.data);
        setHasRun(true);
      } else {
        setError(result.error ?? "Failed to execute report.");
      }

      setIsLoading(false);
    },
    [definition.slug],
  );

  async function handleSubmit(parameters: Record<string, unknown>) {
    setCurrentParams(parameters);
    await runReport(parameters, 0);
  }

  async function handlePageChange(newPage: number) {
    if (!currentParams) return;
    await runReport(currentParams, newPage);
  }

  async function handleExportCsv() {
    if (!currentParams) return;

    const result = await exportReportCsvAction(definition.slug, currentParams);
    if (result.data) {
      const blob = new Blob([result.data], { type: "text/csv" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${definition.slug}-report.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } else {
      setError(result.error ?? "Failed to export CSV.");
    }
  }

  async function handleExportPdf() {
    if (!currentParams) return;

    const result = await exportReportPdfAction(definition.slug, currentParams);
    if (result.data) {
      const byteCharacters = atob(result.data);
      const byteNumbers = new Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNumbers);
      const blob = new Blob([byteArray], { type: "application/pdf" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${definition.slug}-report.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } else {
      setError(result.error ?? "Failed to export PDF.");
    }
  }

  return (
    <div className="space-y-6">
      {/* Parameter Form */}
      <ReportParameterForm
        schema={definition.parameterSchema}
        onSubmit={handleSubmit}
        isLoading={isLoading}
      />

      {/* Export Buttons */}
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={!hasRun || isLoading}
          onClick={handleExportCsv}
        >
          <Download className="mr-1.5 size-4" />
          Export CSV
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={!hasRun || isLoading}
          onClick={handleExportPdf}
        >
          <FileText className="mr-1.5 size-4" />
          Export PDF
        </Button>
      </div>

      {/* Error Message */}
      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-400">
          {error}
        </div>
      )}

      {/* Results */}
      <ReportResults
        response={response}
        isLoading={isLoading}
        onPageChange={handlePageChange}
      />
    </div>
  );
}
