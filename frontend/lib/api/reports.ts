import "server-only";

import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { api, ApiError } from "@/lib/api";

// ---- Response Interfaces ----

export interface ReportListItem {
  slug: string;
  name: string;
  description: string;
}

export interface ReportCategory {
  category: string;
  label: string;
  reports: ReportListItem[];
}

export interface ReportListResponse {
  categories: ReportCategory[];
}

export interface ParameterDefinition {
  name: string;
  type: "date" | "enum" | "uuid";
  label: string;
  required?: boolean;
  options?: string[];
  default?: string;
  entityType?: string;
}

export interface ParameterSchema {
  parameters: ParameterDefinition[];
}

export interface ColumnDefinition {
  key: string;
  label: string;
  type: string;
  format?: string;
}

export interface ColumnDefinitions {
  columns: ColumnDefinition[];
}

export interface ReportDefinitionDetail {
  slug: string;
  name: string;
  description: string;
  category: string;
  parameterSchema: ParameterSchema;
  columnDefinitions: ColumnDefinitions;
  isSystem: boolean;
}

export type ReportRow = Record<string, unknown>;

export interface PaginationInfo {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ReportExecutionResponse {
  reportName: string;
  parameters: Record<string, unknown>;
  generatedAt: string;
  columns: ColumnDefinition[];
  rows: ReportRow[];
  summary: Record<string, unknown>;
  pagination: PaginationInfo;
}

// ---- API Functions ----

export async function getReportDefinitions(): Promise<ReportListResponse> {
  return api.get<ReportListResponse>("/api/report-definitions");
}

export async function getReportDefinition(
  slug: string,
): Promise<ReportDefinitionDetail> {
  return api.get<ReportDefinitionDetail>(`/api/report-definitions/${slug}`);
}

export async function executeReport(
  slug: string,
  parameters: Record<string, unknown>,
  page: number,
  size: number,
): Promise<ReportExecutionResponse> {
  return api.post<ReportExecutionResponse>(
    `/api/report-definitions/${encodeURIComponent(slug)}/execute`,
    { parameters, page, size },
  );
}

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

function buildExportQueryParams(parameters: Record<string, unknown>): string {
  const queryParams = new URLSearchParams();
  for (const [key, value] of Object.entries(parameters)) {
    if (value != null && value !== "") {
      queryParams.set(key, String(value));
    }
  }
  return queryParams.toString();
}

async function getAuthToken(): Promise<string> {
  const { getToken } = await auth();
  const token = await getToken();
  if (!token) {
    redirect("/sign-in");
  }
  return token;
}

export async function exportReportCsv(
  slug: string,
  parameters: Record<string, unknown>,
): Promise<string> {
  const token = await getAuthToken();
  const qs = buildExportQueryParams(parameters);

  const response = await fetch(
    `${BACKEND_URL}/api/report-definitions/${encodeURIComponent(slug)}/export/csv?${qs}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  );

  if (!response.ok) {
    throw new ApiError(response.status, `Export failed: ${response.statusText}`);
  }

  return response.text();
}

export async function exportReportPdf(
  slug: string,
  parameters: Record<string, unknown>,
): Promise<string> {
  const token = await getAuthToken();
  const qs = buildExportQueryParams(parameters);

  const response = await fetch(
    `${BACKEND_URL}/api/report-definitions/${encodeURIComponent(slug)}/export/pdf?${qs}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  );

  if (!response.ok) {
    throw new ApiError(response.status, `Export failed: ${response.statusText}`);
  }

  const buffer = await response.arrayBuffer();
  return Buffer.from(buffer).toString("base64");
}
