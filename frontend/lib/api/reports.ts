import "server-only";

import { api, ApiError, API_BASE, getAuthFetchOptions, resolveServerReturnTo } from "./client";
import { redirectToReLogin } from "@/lib/auth/expiry";
import type { ProblemDetail } from "@/lib/types";

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
  /** Display label per enum option value (the submitted value stays the option). */
  optionLabels?: Record<string, string>;
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

export async function getReportDefinition(slug: string): Promise<ReportDefinitionDetail> {
  return api.get<ReportDefinitionDetail>(`/api/report-definitions/${slug}`);
}

export async function executeReport(
  slug: string,
  parameters: Record<string, unknown>,
  page: number,
  size: number
): Promise<ReportExecutionResponse> {
  return api.post<ReportExecutionResponse>(
    `/api/report-definitions/${encodeURIComponent(slug)}/execute`,
    { parameters, page, size }
  );
}

function buildExportQueryParams(parameters: Record<string, unknown>): string {
  const queryParams = new URLSearchParams();
  for (const [key, value] of Object.entries(parameters)) {
    if (value != null && value !== "") {
      queryParams.set(key, String(value));
    }
  }
  return queryParams.toString();
}

function withQuery(path: string, qs: string): string {
  return qs ? `${path}?${qs}` : path;
}

/**
 * GET an export endpoint through the auth-mode-aware API base: the gateway with the forwarded
 * SESSION cookie in Keycloak BFF mode, the backend with the mock token in mock mode (the same
 * contract as the audit-log exports). Only the auth headers are sent, so a denied caller gets the
 * backend's 403 problem+json and its detail becomes the ApiError message.
 */
async function fetchExport(path: string): Promise<Response> {
  let auth: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    auth = await getAuthFetchOptions("GET");
  } catch {
    // No usable session — funnel to the branded re-login route.
    redirectToReLogin(await resolveServerReturnTo(), "expired");
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method: "GET",
    headers: auth.headers,
    credentials: auth.credentials,
    // A redirect here means the session is gone (a login page); surface it as 401 rather than
    // following it and handing the login HTML back as the export.
    redirect: "manual",
  });

  if (response.status >= 300 && response.status < 400) {
    throw new ApiError(401, "Authentication session expired");
  }
  if (!response.ok) {
    let detail: ProblemDetail | undefined;
    try {
      const parsed = (await response.json()) as unknown;
      // RFC 9457 ProblemDetail body — guard the shape before forwarding.
      if (parsed && typeof parsed === "object") {
        detail = parsed as ProblemDetail;
      }
    } catch {
      // Body wasn't JSON — fall through with the status text only.
    }
    const message =
      (typeof detail?.detail === "string" && detail.detail) ||
      `Export failed: ${response.statusText}`;
    throw new ApiError(response.status, message, detail);
  }
  return response;
}

export async function exportReportCsv(
  slug: string,
  parameters: Record<string, unknown>
): Promise<string> {
  const qs = buildExportQueryParams(parameters);
  const response = await fetchExport(
    withQuery(`/api/report-definitions/${encodeURIComponent(slug)}/export/csv`, qs)
  );
  return response.text();
}

/** Returns the PDF bytes base64-encoded (server actions can only return serializable values). */
export async function exportReportPdf(
  slug: string,
  parameters: Record<string, unknown>
): Promise<string> {
  const qs = buildExportQueryParams(parameters);
  const response = await fetchExport(
    withQuery(`/api/report-definitions/${encodeURIComponent(slug)}/export/pdf`, qs)
  );
  const buffer = await response.arrayBuffer();
  return Buffer.from(buffer).toString("base64");
}

/**
 * GET /api/reports/profitability/export/csv — the Profitability page's Matter rows as CSV for the
 * given range; empty dates are omitted from the query.
 */
export async function exportProfitabilityCsv(from?: string, to?: string): Promise<string> {
  const qs = buildExportQueryParams({ from, to });
  const response = await fetchExport(withQuery("/api/reports/profitability/export/csv", qs));
  return response.text();
}
