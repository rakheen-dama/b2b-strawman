import "server-only";

import { api } from "./client";

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

// === API Function ===

export async function listAuditEvents(params?: {
  page?: number;
  size?: number;
}): Promise<AuditEventsPage> {
  const searchParams = new URLSearchParams();
  searchParams.set("page", String(params?.page ?? 0));
  searchParams.set("size", String(params?.size ?? 50));
  return api.get<AuditEventsPage>(`/api/audit-events?${searchParams.toString()}`);
}
