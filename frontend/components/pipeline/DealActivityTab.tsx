"use client";

import { AuditTimelineTab } from "@/components/audit/audit-timeline-tab";

/**
 * Client wrapper around `<AuditTimelineTab>` for the deal detail page.
 * Provides the TEAM_OVERSIGHT capability gate so the server-rendered page can
 * pass a serializable element into the deal activity tab.
 *
 * Entity-type casing note: the backend records deal lifecycle audit rows with
 * mixed casing — DealService (creation/update) uses `"deal"` while
 * DealTransitionService uses `"DEAL"`. The audit query matches case-sensitively.
 * We query `"deal"` to mirror the project precedent (ProjectAuditTab uses
 * `"project"`) and the deal creation/update events. (Backend casing inconsistency
 * is out of scope for this frontend-only epic.)
 */
export function DealActivityTab({ dealId }: { dealId: string }) {
  return <AuditTimelineTab entityType="deal" entityId={dealId} />;
}
