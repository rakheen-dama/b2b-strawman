"use client";

import { AuditTimelineTab } from "@/components/audit/audit-timeline-tab";

/**
 * Client wrapper around `<AuditTimelineTab>` for the project detail page.
 * Provides the capability gate so the server-rendered page can pass a
 * serializable element into the project tab strip.
 */
export function ProjectAuditTab({ projectId }: { projectId: string }) {
  return <AuditTimelineTab entityType="project" entityId={projectId} />;
}
