/**
 * Client-callable API wrapper for `/api/assistant/specialists/*` endpoints.
 *
 * NOTE: This module intentionally does NOT use `"server-only"`. The Phase 70
 * specialist launcher is a `"use client"` component and must invoke these
 * functions directly from the browser (matching the pattern of
 * `frontend/hooks/use-assistant-chat.ts`).
 *
 * Backend contract: see Phase 70 Epic 511A — `SpecialistController`.
 */

// ---- Types ----

export interface LauncherContext {
  route: string;
  surface: string;
  ctaLabel: string;
}

export interface SpecialistSummary {
  id: string;
  displayName: string;
  tagline: string;
  launchers: LauncherContext[];
}

export interface SpecialistDetail extends SpecialistSummary {
  toolIds: string[];
  automationCapable: boolean;
  maxToolIterations: number;
}

export interface ContextRef {
  entityType: string;
  entityId: string;
  currentPage?: string;
}

export interface SessionHandle {
  /** Backend returns a UUID; serialized as a string in JSON. */
  sessionId: string;
  specialistId: string;
  systemPromptHash: string;
  toolIds: string[];
  displayName: string;
  preSeededAssistantMessage: string | null;
}

export interface StartSessionRequest {
  contextRef: ContextRef;
  initialPrompt?: string;
  /** Toolbar/surface identifier — propagated for backend analytics/logging. */
  surface?: string;
}

// ---- Auth helpers (mirror use-assistant-chat.ts) ----

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
const API_BASE =
  AUTH_MODE === "keycloak" ? "" : process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (typeof document === "undefined") return headers;
  if (AUTH_MODE === "mock") {
    const match = document.cookie.match(/(?:^|;\s*)mock-auth-token=([^;]+)/);
    if (match) {
      headers["Authorization"] = `Bearer ${decodeURIComponent(match[1])}`;
    }
  }
  if (AUTH_MODE === "keycloak") {
    const xsrf = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    if (xsrf) {
      headers["X-XSRF-TOKEN"] = decodeURIComponent(xsrf[1]);
    }
  }
  return headers;
}

function credentialsMode(): RequestCredentials {
  return AUTH_MODE === "keycloak" ? "include" : "omit";
}

// ---- Errors ----

export class SpecialistApiError extends Error {
  readonly status: number;
  readonly body: string;
  constructor(status: number, body: string, message?: string) {
    super(message || `Specialist API error: ${status}`);
    this.name = "SpecialistApiError";
    this.status = status;
    this.body = body;
  }
}

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new SpecialistApiError(response.status, body);
  }
  return (await response.json()) as T;
}

// ---- Approve payload types ----

/** Polish specialist — accepted edits to apply to time-entry descriptions. */
export interface BillingPolishAppliedOutput {
  kind: "BillingPolishPayload";
  invoiceId: string;
  edits: { timeEntryId: string; beforeText: string; afterText: string }[];
}

/** Grouping specialist — proposed line-item groups. */
export interface BillingGroupingAppliedOutput {
  kind: "BillingGroupingPayload";
  invoiceId: string;
  groups: { description: string; hours: number; sourceTimeEntryIds: string[] }[];
}

/** Intake specialist — proposed customer field extraction. */
export interface IntakeExtractionAppliedOutput {
  kind: "IntakeExtractionPayload";
  contextEntityType: string;
  contextEntityId: string;
  proposedFields: Record<string, unknown>;
  extractionPath: "TEXT" | "VISION";
  popiaFlaggedFields: string[];
  validationFlags: string[];
}

/**
 * Discriminated union of all specialist applied-output payloads.
 * Future specialists (514B) add their shapes here.
 */
export type AppliedOutput =
  | BillingPolishAppliedOutput
  | BillingGroupingAppliedOutput
  | IntakeExtractionAppliedOutput;

// ---- API ----

export async function listSpecialists(route?: string): Promise<SpecialistSummary[]> {
  const qs = route ? `?route=${encodeURIComponent(route)}` : "";
  const res = await fetch(`${API_BASE}/api/assistant/specialists${qs}`, {
    method: "GET",
    headers: getAuthHeaders(),
    credentials: credentialsMode(),
  });
  return handleJson<SpecialistSummary[]>(res);
}

export async function getSpecialist(id: string): Promise<SpecialistDetail> {
  const res = await fetch(`${API_BASE}/api/assistant/specialists/${encodeURIComponent(id)}`, {
    method: "GET",
    headers: getAuthHeaders(),
    credentials: credentialsMode(),
  });
  return handleJson<SpecialistDetail>(res);
}

export async function startSession(id: string, body: StartSessionRequest): Promise<SessionHandle> {
  const res = await fetch(
    `${API_BASE}/api/assistant/specialists/${encodeURIComponent(id)}/sessions`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      credentials: credentialsMode(),
      body: JSON.stringify(body),
    }
  );
  return handleJson<SessionHandle>(res);
}

export async function approveInvocation(
  invocationId: string,
  appliedOutput?: AppliedOutput
): Promise<{ id: string; status: string; appliedAt: string }> {
  const body = appliedOutput ? { appliedOutput } : {};
  const res = await fetch(
    `${API_BASE}/api/assistant/invocations/${encodeURIComponent(invocationId)}/approve`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      credentials: credentialsMode(),
      body: JSON.stringify(body),
    }
  );
  return handleJson(res);
}

export async function rejectInvocation(invocationId: string, rejectReason: string): Promise<void> {
  const res = await fetch(
    `${API_BASE}/api/assistant/invocations/${encodeURIComponent(invocationId)}/reject`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      credentials: credentialsMode(),
      body: JSON.stringify({ rejectReason }),
    }
  );
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new SpecialistApiError(res.status, body);
  }
}

export async function retryInvocation(invocationId: string): Promise<void> {
  const res = await fetch(
    `${API_BASE}/api/assistant/invocations/${encodeURIComponent(invocationId)}/retry`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      credentials: credentialsMode(),
    }
  );
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new SpecialistApiError(res.status, body);
  }
}

export interface BulkApproveResult {
  approved: string[];
  failed: { id: string; reason: string }[];
}

export async function bulkApproveInvocations(ids: string[]): Promise<BulkApproveResult> {
  const res = await fetch(`${API_BASE}/api/assistant/invocations/bulk-approve`, {
    method: "POST",
    headers: getAuthHeaders(),
    credentials: credentialsMode(),
    body: JSON.stringify({ ids }),
  });
  return handleJson<BulkApproveResult>(res);
}

export interface InvocationListItemClient {
  id: string;
  specialistId: string;
  invokedBy: string;
  status: string;
  contextEntityType: string;
  contextEntityId: string;
  createdAt: string;
  proposedOutputSummary: string | null;
  automationActionExecutionId: string | null;
}

export interface InvocationPageClient {
  content: InvocationListItemClient[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

export async function listInvocationsClient(params: Record<string, string>): Promise<InvocationPageClient> {
  const qs = new URLSearchParams(params).toString();
  const res = await fetch(`${API_BASE}/api/assistant/invocations${qs ? `?${qs}` : ""}`, {
    method: "GET",
    headers: getAuthHeaders(),
    credentials: credentialsMode(),
  });
  return handleJson<InvocationPageClient>(res);
}

export interface InvocationDetailClient {
  id: string;
  specialistId: string;
  invokedBy: string;
  actorId: string | null;
  automationActionExecutionId: string | null;
  contextEntityType: string;
  contextEntityId: string;
  status: string;
  proposedOutput: Record<string, unknown> | null;
  appliedOutput: Record<string, unknown> | null;
  createdAt: string;
  reviewedAt: string | null;
  reviewedById: string | null;
  rejectReason: string | null;
  errorMessage: string | null;
  promptVersion: string | null;
  version: number;
}

export async function getInvocationClient(id: string): Promise<InvocationDetailClient> {
  const res = await fetch(
    `${API_BASE}/api/assistant/invocations/${encodeURIComponent(id)}`,
    {
      method: "GET",
      headers: getAuthHeaders(),
      credentials: credentialsMode(),
    }
  );
  return handleJson<InvocationDetailClient>(res);
}

/**
 * Authenticated SWR fetcher for use in client components.
 * Uses the same auth headers and credentials mode as all other client API calls.
 */
export async function authFetcher<T>(url: string): Promise<T> {
  const fullUrl = url.startsWith("http") ? url : `${API_BASE}${url}`;
  const res = await fetch(fullUrl, {
    method: "GET",
    headers: getAuthHeaders(),
    credentials: credentialsMode(),
  });
  return handleJson<T>(res);
}
