"use client";

import {
  AuditTimeline,
  type AuditTimelineProps,
} from "@/components/audit/audit-timeline";
import { CAPABILITIES, RequiresCapability, useCapabilities } from "@/lib/capabilities";

/**
 * Capability-gated wrapper around `<AuditTimeline>`. Members without the
 * `TEAM_OVERSIGHT` capability see nothing — the parent tab strip should also
 * hide the tab via `useAuditTabVisible()` so the user does not land on an
 * empty pane.
 */
export function AuditTimelineTab(props: AuditTimelineProps) {
  return (
    <RequiresCapability cap={CAPABILITIES.TEAM_OVERSIGHT} fallback={null}>
      <AuditTimeline {...props} />
    </RequiresCapability>
  );
}

/**
 * Read whether the current viewer should see the Audit tab. Use in tab strips
 * to conditionally render the tab trigger / content slot.
 */
export function useAuditTabVisible(): boolean {
  const { hasCapability, isLoading } = useCapabilities();
  if (isLoading) return false;
  return hasCapability(CAPABILITIES.TEAM_OVERSIGHT);
}
