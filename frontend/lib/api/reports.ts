import "server-only";

import { api } from "@/lib/api";

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
    `/api/report-definitions/${slug}/execute`,
    { parameters, page, size },
  );
}
