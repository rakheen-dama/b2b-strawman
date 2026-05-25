import "server-only";

import { api } from "./client";
import type { AiGateListItem } from "./ai";

// ---- Types ----

export interface CategoryScore {
  grade: string;
  compliant: number;
  nonCompliant: number;
  critical: number;
}

export interface FindingCounts {
  critical: number;
  high: number;
  medium: number;
  low: number;
  info: number;
}

export interface ComplianceAuditReportResponse {
  id: string;
  overallGrade: string;
  overallAssessment: string;
  status: string;
  categoryScores: Record<string, CategoryScore>;
  findingCounts: FindingCounts | null;
  publishedAt: string | null;
  publishedBy: string | null;
}

export interface EntityReference {
  type: string;
  id: string;
  name: string;
}

export interface AuditFinding {
  id: string;
  severity: string;
  category: string;
  title: string;
  description: string;
  regulatoryBasis: string | null;
  remediation: string | null;
  entityReferences: EntityReference[];
}

export interface Recommendation {
  priority: string;
  recommendation: string;
  estimatedEffort: string;
}

export interface ComplianceAuditOutput {
  auditDate: string;
  overallGrade: string;
  overallAssessment: string;
  categoryScores: Record<string, CategoryScore>;
  findings: AuditFinding[];
  recommendations: Recommendation[];
}

export interface ComplianceAuditInvokeResponse {
  executionId: string;
  status: string;
  output: ComplianceAuditOutput | null;
  gates: AiGateListItem[];
  costCents: number;
  model: string;
  durationMs: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

// ---- Finding Types ----

export interface ComplianceAuditFindingResponse {
  id: string;
  findingId: string;
  severity: string;
  category: string;
  title: string;
  description: string;
  regulatoryBasis: string | null;
  remediation: string | null;
  entityType: string | null;
  entityId: string | null;
  status: string;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionNotes: string | null;
}

export interface FindingFilters {
  severity?: string[];
  category?: string[];
  status?: string[];
}

export interface UpdateFindingStatusRequest {
  status: string;
  resolutionNotes?: string;
}

// ---- API Functions ----

export async function invokeComplianceAudit(): Promise<ComplianceAuditInvokeResponse> {
  const raw = await api.post<{
    executionId: string;
    status: string;
    output: string | null;
    gates: AiGateListItem[];
    costCents: number;
    model: string;
    durationMs: number;
  }>("/api/ai/skills/compliance-audit", {});

  let parsedOutput: ComplianceAuditOutput | null = null;
  if (raw.output) {
    try {
      parsedOutput = JSON.parse(raw.output) as ComplianceAuditOutput;
    } catch {
      parsedOutput = null;
    }
  }

  return {
    ...raw,
    output: parsedOutput,
  };
}

export async function getAuditReports(
  page?: number,
  size?: number
): Promise<PaginatedResponse<ComplianceAuditReportResponse>> {
  const searchParams = new URLSearchParams();
  if (page !== undefined) searchParams.set("page", String(page));
  if (size !== undefined) searchParams.set("size", String(size));
  const qs = searchParams.toString();
  return api.get<PaginatedResponse<ComplianceAuditReportResponse>>(
    `/api/compliance/audit-reports${qs ? `?${qs}` : ""}`
  );
}

export async function getAuditReport(id: string): Promise<ComplianceAuditReportResponse> {
  return api.get<ComplianceAuditReportResponse>(`/api/compliance/audit-reports/${id}`);
}

// ---- Finding API Functions ----

export async function getAuditFindings(
  reportId: string,
  filters?: FindingFilters,
  page?: number,
  size?: number
): Promise<PaginatedResponse<ComplianceAuditFindingResponse>> {
  const searchParams = new URLSearchParams();
  if (filters?.severity?.length) {
    searchParams.set("severity", filters.severity.join(","));
  }
  if (filters?.category?.length) {
    searchParams.set("category", filters.category.join(","));
  }
  if (filters?.status?.length) {
    searchParams.set("status", filters.status.join(","));
  }
  if (page !== undefined) searchParams.set("page", String(page));
  if (size !== undefined) searchParams.set("size", String(size));
  const qs = searchParams.toString();
  return api.get<PaginatedResponse<ComplianceAuditFindingResponse>>(
    `/api/compliance/audit-reports/${reportId}/findings${qs ? `?${qs}` : ""}`
  );
}

export async function updateFindingStatus(
  reportId: string,
  findingId: string,
  status: string,
  resolutionNotes?: string
): Promise<ComplianceAuditFindingResponse> {
  return api.patch<ComplianceAuditFindingResponse>(
    `/api/compliance/audit-reports/${reportId}/findings/${findingId}`,
    { status, resolutionNotes }
  );
}
