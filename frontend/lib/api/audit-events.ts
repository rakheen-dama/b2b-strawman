import "server-only";

import { api } from "./client";

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
