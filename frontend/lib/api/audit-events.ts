import "server-only";

import { API_BASE, ApiError, api, getAuthFetchOptions } from "./client";
import type { ProblemDetail } from "@/lib/types/common";

const EXPORT_TIMEOUT_MS = 30_000;

/**
 * fetch wrapper that aborts after EXPORT_TIMEOUT_MS. Used by the export
 * helpers because the upstream PDF/CSV endpoints can otherwise hang
 * indefinitely on a stalled stream.
 */
async function fetchWithTimeout(
  url: string,
  init: RequestInit & { timeoutMs?: number } = {}
): Promise<Response> {
  const { timeoutMs = EXPORT_TIMEOUT_MS, ...rest } = init;
  const controller = new AbortController();
  const handle = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...rest, signal: controller.signal });
  } finally {
    clearTimeout(handle);
  }
}

// === Enums ===

export type AuditSeverity = "INFO" | "NOTICE" | "WARNING" | "CRITICAL";
export type AuditEventGroup = "SECURITY" | "COMPLIANCE" | "FINANCIAL" | "DATA" | "STANDARD";

// === Response DTO ===

export interface AuditEventResponse {
  id: string;
  eventType: string;
  entityType: string;
  entityId: string | null;
  actorId: string | null;
  actorType: string | null;
  source: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  details: Record<string, unknown> | null;
  occurredAt: string;
  // Read-time enrichment (502.8):
  label: string;
  severity: AuditSeverity;
  group: AuditEventGroup;
  actorDisplayName: string;
}

export interface AuditEventsPage {
  content: AuditEventResponse[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

export interface AuditEventTypeMetadata {
  eventType: string;
  label: string;
  severity: AuditSeverity;
  group: AuditEventGroup;
}

export interface ActorFacet {
  actorId: string;
  actorDisplayName: string;
  actorType: string;
  eventCount: number;
}

export interface EventTypeFacet {
  eventType: string;
  label: string;
  severity: AuditSeverity;
  group: AuditEventGroup;
  count: number;
}

export interface EntityTypeFacet {
  entityType: string;
  label: string;
  count: number;
}

// === Filter shape ===

export interface AuditEventFilter {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
  severities?: AuditSeverity[];
  actorId?: string;
  eventType?: string;
  entityType?: string;
  entityId?: string;
}

function buildAuditEventQuery(params?: AuditEventFilter): URLSearchParams {
  const sp = new URLSearchParams();
  sp.set("page", String(params?.page ?? 0));
  sp.set("size", String(params?.size ?? 50));
  if (params?.from) sp.set("from", params.from);
  if (params?.to) sp.set("to", params.to);
  if (params?.severities && params.severities.length > 0) {
    sp.set("severities", params.severities.join(","));
  }
  if (params?.actorId) sp.set("actorId", params.actorId);
  if (params?.eventType) sp.set("eventType", params.eventType);
  if (params?.entityType) sp.set("entityType", params.entityType);
  if (params?.entityId) sp.set("entityId", params.entityId);
  return sp;
}

// === API Functions ===

export async function listAuditEvents(params?: AuditEventFilter): Promise<AuditEventsPage> {
  const sp = buildAuditEventQuery(params);
  return api.get<AuditEventsPage>(`/api/audit-events?${sp.toString()}`);
}

export async function getAuditMetadata(): Promise<AuditEventTypeMetadata[]> {
  return api.get<AuditEventTypeMetadata[]>("/api/audit-events/metadata");
}

function buildFacetQuery(params?: { from?: string; to?: string }): string {
  const sp = new URLSearchParams();
  if (params?.from) sp.set("from", params.from);
  if (params?.to) sp.set("to", params.to);
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export async function listFacetActors(params?: {
  from?: string;
  to?: string;
}): Promise<ActorFacet[]> {
  return api.get<ActorFacet[]>(`/api/audit-events/facets/actors${buildFacetQuery(params)}`);
}

export async function listFacetEventTypes(params?: {
  from?: string;
  to?: string;
}): Promise<EventTypeFacet[]> {
  return api.get<EventTypeFacet[]>(
    `/api/audit-events/facets/event-types${buildFacetQuery(params)}`
  );
}

export async function listFacetEntityTypes(params?: {
  from?: string;
  to?: string;
}): Promise<EntityTypeFacet[]> {
  return api.get<EntityTypeFacet[]>(
    `/api/audit-events/facets/entity-types${buildFacetQuery(params)}`
  );
}

// === Epic 506B: Count + Export helpers ===

/**
 * Returns the total number of events matching the filter.
 *
 * The backend has no `?count=true` route — we re-use the list endpoint with
 * `size=1` and read `page.totalElements` (Spring `Page<T>` semantics).
 */
export async function countAuditEvents(filter: AuditEventFilter): Promise<number> {
  const page = await listAuditEvents({ ...filter, page: 0, size: 1 });
  return page.page.totalElements;
}

function buildExportQuery(filter: AuditEventFilter): URLSearchParams {
  const sp = buildAuditEventQuery(filter);
  // export endpoints ignore page/size — strip to keep URL minimal.
  sp.delete("page");
  sp.delete("size");
  return sp;
}

/**
 * Streams the audit log as CSV text. No row cap.
 */
export async function exportAuditCsv(filter: AuditEventFilter): Promise<string> {
  const qs = buildExportQuery(filter);
  const auth = await getAuthFetchOptions("GET");
  const response = await fetchWithTimeout(
    `${API_BASE}/api/audit-events/export.csv?${qs.toString()}`,
    {
      headers: auth.headers,
      credentials: auth.credentials,
    }
  );
  if (!response.ok) {
    throw new ApiError(response.status, `Export failed: ${response.statusText}`);
  }
  return response.text();
}

/**
 * Streams the audit log as a PDF (returns base64 string for the client to
 * decode into a Blob). 10000-row cap enforced server-side; over-cap returns
 * 413 with a ProblemDetail body that includes `rowCount` + `cap`.
 */
export async function exportAuditPdf(filter: AuditEventFilter): Promise<string> {
  const qs = buildExportQuery(filter);
  const auth = await getAuthFetchOptions("GET");
  const response = await fetchWithTimeout(
    `${API_BASE}/api/audit-events/export.pdf?${qs.toString()}`,
    {
      headers: auth.headers,
      credentials: auth.credentials,
    }
  );
  if (!response.ok) {
    let detail: ProblemDetail | undefined;
    try {
      const parsed = (await response.json()) as unknown;
      // RFC 9457 ProblemDetail body — guard the shape before forwarding.
      if (parsed && typeof parsed === "object") {
        detail = parsed as ProblemDetail;
      }
    } catch {
      // body wasn't JSON — fall through with status text only
    }
    const message =
      (typeof detail?.detail === "string" && detail.detail) ||
      `Export failed: ${response.statusText}`;
    throw new ApiError(response.status, message, detail);
  }
  const buffer = await response.arrayBuffer();
  return Buffer.from(buffer).toString("base64");
}
