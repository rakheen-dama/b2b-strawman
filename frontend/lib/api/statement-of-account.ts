import "server-only";

import { api } from "./client";

// ---- Request types ----

export interface GenerateStatementRequest {
  periodStart: string; // ISO yyyy-MM-dd
  periodEnd: string; // ISO yyyy-MM-dd
  templateId?: string; // optional UUID; defaults to system statement-of-account slug
}

// ---- Response types ----

export interface StatementSummary {
  totalFees: number;
  totalDisbursements: number;
  previousBalanceOwing: number;
  paymentsReceived: number;
  closingBalanceOwing: number;
  trustBalanceHeld: number;
}

export interface MatterRef {
  projectId: string;
  name: string;
}

export interface StatementResponse {
  id: string;
  templateId: string;
  generatedAt: string; // ISO datetime
  htmlPreview: string | null; // present only on POST 201
  pdfUrl: string;
  matter: MatterRef;
  summary: StatementSummary;
}

export interface PaginatedStatementsResponse {
  content: StatementResponse[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

// ---- API functions ----

export async function generateStatement(
  projectId: string,
  req: GenerateStatementRequest
): Promise<StatementResponse> {
  return api.post<StatementResponse>(
    `/api/matters/${projectId}/statements`,
    req
  );
}

export async function listStatements(
  projectId: string,
  page: number = 0,
  size: number = 20
): Promise<PaginatedStatementsResponse> {
  const search = new URLSearchParams();
  search.set("page", String(page));
  search.set("size", String(size));
  return api.get<PaginatedStatementsResponse>(
    `/api/matters/${projectId}/statements?${search.toString()}`
  );
}

export async function getStatement(
  projectId: string,
  id: string
): Promise<StatementResponse> {
  return api.get<StatementResponse>(
    `/api/matters/${projectId}/statements/${id}`
  );
}
