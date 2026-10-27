"use server";

import { listAuditEventsByEntity } from "@/lib/api/audit-events";
import type { AuditEventsPage } from "@/lib/api/audit-events";
import { getClientActivity } from "@/lib/api/clients";

/**
 * Server action wrapper around `listAuditEventsByEntity` so that the
 * `"use client"` <AuditTimeline> component can fetch a page of audit events
 * without importing the `server-only` audit-events client directly.
 *
 * Capability gating (`TEAM_OVERSIGHT`) is enforced server-side by the
 * underlying REST endpoint — the action is a thin pass-through.
 */
export async function fetchEntityAuditPage(
  entityType: string,
  entityId: string,
  page: number,
  size: number
): Promise<AuditEventsPage> {
  return listAuditEventsByEntity(entityType, entityId, { page, size });
}

/**
 * Server action for the client Activity tab (625D): one page of the three-branch client feed
 * (`GET /api/clients/{id}/activity` — the client's own rows, its checklist / information-request
 * children, and rows whose details carry the client id). Same row shape as
 * {@link fetchEntityAuditPage}; `TEAM_OVERSIGHT` is enforced by the endpoint.
 */
export async function fetchClientActivityPage(
  clientId: string,
  page: number,
  size: number
): Promise<AuditEventsPage> {
  return getClientActivity(clientId, { page, size });
}
