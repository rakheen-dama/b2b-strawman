"use client";

import { AuditTimelineTab } from "@/components/audit/audit-timeline-tab";
import { CAPABILITIES, useCapabilities } from "@/lib/capabilities";

/**
 * Audit section appended to the invoice detail page. The invoice detail
 * surface does not currently use a tab strip, so we render a labelled section
 * matching the "Generated Documents" heading style. The section is hidden
 * entirely when the viewer lacks the `TEAM_OVERSIGHT` capability so the
 * heading does not orphan above an empty pane.
 */
export function InvoiceAuditSection({ invoiceId }: { invoiceId: string }) {
  const { hasCapability, isLoading } = useCapabilities();

  if (isLoading || !hasCapability(CAPABILITIES.TEAM_OVERSIGHT)) {
    return null;
  }

  return (
    <div className="space-y-4" data-testid="invoice-audit-section">
      <h2 className="font-display text-lg text-slate-950 dark:text-slate-50">Audit trail</h2>
      <AuditTimelineTab entityType="invoice" entityId={invoiceId} />
    </div>
  );
}
