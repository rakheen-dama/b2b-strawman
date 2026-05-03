"use server";

import { listAuditEventsByEntity } from "@/lib/api/audit-events";
import type { AuditEventsPage } from "@/lib/api/audit-events";

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
