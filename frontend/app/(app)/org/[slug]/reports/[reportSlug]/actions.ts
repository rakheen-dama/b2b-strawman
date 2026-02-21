"use server";

import {
  executeReport,
  exportReportCsv,
  exportReportPdf,
  type ReportExecutionResponse,
} from "@/lib/api/reports";
import { ApiError } from "@/lib/api";

interface ActionResult<T> {
  data: T | null;
  error?: string;
}

export async function executeReportAction(
  slug: string,
  parameters: Record<string, unknown>,
  page: number,
  size: number,
): Promise<ActionResult<ReportExecutionResponse>> {
  try {
    const data = await executeReport(slug, parameters, page, size);
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}

export async function exportReportCsvAction(
  slug: string,
  parameters: Record<string, unknown>,
): Promise<ActionResult<string>> {
  try {
    const data = await exportReportCsv(slug, parameters);
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "Failed to export CSV." };
  }
}

export async function exportReportPdfAction(
  slug: string,
  parameters: Record<string, unknown>,
): Promise<ActionResult<string>> {
  try {
    const data = await exportReportPdf(slug, parameters);
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "Failed to export PDF." };
  }
}
