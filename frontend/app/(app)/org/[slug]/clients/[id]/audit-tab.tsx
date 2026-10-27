"use client";

import { useCallback } from "react";

import { AuditTimelineTab } from "@/components/audit/audit-timeline-tab";
import { fetchClientActivityPage } from "@/lib/actions/audit-events";

/**
 * Client wrapper around `<AuditTimelineTab>` for the client detail page (Activity tab).
 * The page is a Server Component, so the capability gate (which uses the
 * client-side `useCapabilities()` hook) lives here.
 *
 * Reads the paged three-branch client feed (`GET /api/clients/{id}/activity`, 625C) rather than
 * the strict by-entity query, so the client's FICA checklist and information-request events show
 * up next to its own rows (G-B2).
 */
export function CustomerAuditTab({ customerId }: { customerId: string }) {
  // Memoised: the timeline refetches whenever its loader identity changes.
  const loadPage = useCallback(
    (page: number, size: number) => fetchClientActivityPage(customerId, page, size),
    [customerId]
  );
  return <AuditTimelineTab entityType="customer" entityId={customerId} loadPage={loadPage} />;
}
