"use client";

import { AuditTimelineTab } from "@/components/audit/audit-timeline-tab";

/**
 * Client wrapper around `<AuditTimelineTab>` for the customer detail page.
 * The page is a Server Component, so the capability gate (which uses the
 * client-side `useCapabilities()` hook) lives here.
 */
export function CustomerAuditTab({ customerId }: { customerId: string }) {
  return <AuditTimelineTab entityType="customer" entityId={customerId} />;
}
