import "server-only";

import { api, ApiError } from "./client";

// ---- Enums / unions ----

export type ClosureReason = "CONCLUDED" | "CLIENT_TERMINATED" | "REFERRED_OUT" | "OTHER";

export type ClosureGateCode =
  | "TRUST_BALANCE_ZERO"
  | "ALL_DISBURSEMENTS_APPROVED"
  | "ALL_DISBURSEMENTS_SETTLED"
  | "FINAL_BILL_ISSUED"
  | "NO_OPEN_COURT_DATES"
  | "NO_OPEN_PRESCRIPTIONS"
  | "ALL_TASKS_RESOLVED"
  | "ALL_INFO_REQUESTS_CLOSED"
  | "ALL_ACCEPTANCE_REQUESTS_FINAL";

// ---- Response types ----

export interface GateResult {
  order: number;
  // tolerant — backend may add new gate codes
  code: ClosureGateCode | string;
  passed: boolean;
  message: string;
  detail: Record<string, unknown> | null;
}

export interface ClosureReport {
  projectId: string;
  evaluatedAt: string;
  allPassed: boolean;
  gates: GateResult[];
}

export interface CloseMatterResponse {
  projectId: string;
  status: "CLOSED";
  closedAt: string;
  closureLogId: string;
  closureLetterDocumentId: string | null;
  retentionEndsAt: string;
}

export interface ReopenMatterResponse {
  projectId: string;
  status: "ACTIVE";
  reopenedAt: string;
  closureLogId: string;
}

export interface ClosureLogEntry {
  id: string;
  projectId: string;
  closedBy: string;
  closedAt: string;
  reason: string;
  notes: string | null;
  gateReport: Record<string, unknown>;
  overrideUsed: boolean;
  overrideJustification: string | null;
  closureLetterDocumentId: string | null;
  reopenedAt: string | null;
  reopenedBy: string | null;
  reopenNotes: string | null;
}

// ---- Request types ----

export interface CloseMatterRequest {
  reason: ClosureReason;
  notes?: string;
  generateClosureLetter: boolean;
  override: boolean;
  overrideJustification?: string;
}

export interface ReopenMatterRequest {
  notes: string;
}

// ---- Typed error surface ----

export const CLOSURE_GATES_FAILED_PROBLEM_TYPE =
  "https://kazi.app/problems/closure-gates-failed";

/**
 * Thrown by `closeMatter()` when the backend returns 409 with
 * `type = "https://kazi.app/problems/closure-gates-failed"`. The full
 * gate report is available on `.report` for the caller to re-render.
 */
export class ClosureGatesFailedError extends Error {
  constructor(
    public readonly report: ClosureReport,
    public readonly status: number = 409
  ) {
    super(`Closure gates failed: ${report.gates.filter((g) => !g.passed).length} failing`);
    this.name = "ClosureGatesFailedError";
  }
}

// ---- API functions ----

export async function evaluateClosure(projectId: string): Promise<ClosureReport> {
  return api.get<ClosureReport>(`/api/matters/${projectId}/closure/evaluate`);
}

export async function closeMatter(
  projectId: string,
  req: CloseMatterRequest
): Promise<CloseMatterResponse> {
  try {
    return await api.post<CloseMatterResponse>(
      `/api/matters/${projectId}/closure/close`,
      req
    );
  } catch (err) {
    if (
      err instanceof ApiError &&
      err.status === 409 &&
      err.detail?.type === CLOSURE_GATES_FAILED_PROBLEM_TYPE &&
      err.detail?.report
    ) {
      throw new ClosureGatesFailedError(err.detail.report as ClosureReport);
    }
    throw err;
  }
}

export async function reopenMatter(
  projectId: string,
  notes: string
): Promise<ReopenMatterResponse> {
  return api.post<ReopenMatterResponse>(`/api/matters/${projectId}/closure/reopen`, {
    notes,
  });
}

export async function listClosureLog(projectId: string): Promise<ClosureLogEntry[]> {
  return api.get<ClosureLogEntry[]>(`/api/matters/${projectId}/closure/log`);
}
