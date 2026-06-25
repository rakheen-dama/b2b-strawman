"use client";

import { AuditTimelineTab } from "@/components/audit/audit-timeline-tab";

/**
 * Client wrapper around `<AuditTimelineTab>` for the deal detail page.
 * Provides the TEAM_OVERSIGHT capability gate so the server-rendered page can
 * pass a serializable element into the deal activity tab.
 *
 * Queries audit events by lowercase `"deal"` entityType — consistent with all deal
 * write sites (DealService, DealTransitionService, DealProposalService).
 */
export function DealActivityTab({ dealId }: { dealId: string }) {
  return <AuditTimelineTab entityType="deal" entityId={dealId} />;
}
