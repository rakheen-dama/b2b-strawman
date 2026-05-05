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

/**
 * Discriminated union of all specialist applied-output payloads.
 * Future specialists (513B, 514B) add their shapes here.
 */
export type AppliedOutput = BillingPolishAppliedOutput | BillingGroupingAppliedOutput;

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

export async function rejectInvocation(
  invocationId: string,
  rejectReason: string
): Promise<void> {
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
