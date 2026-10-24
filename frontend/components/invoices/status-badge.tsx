import { Badge } from "@b2mash/ui/badge";
import type { InvoiceStatus } from "@/lib/types";

type InvoiceBadgeVariant = "neutral" | "lead" | "warning" | "success" | "destructive";

const STATUS_BADGE: Record<InvoiceStatus, { label: string; variant: InvoiceBadgeVariant }> = {
  DRAFT: { label: "Draft", variant: "neutral" },
  APPROVED: { label: "Approved", variant: "lead" },
  SENT: { label: "Sent", variant: "lead" },
  PARTIALLY_PAID: { label: "Partially Paid", variant: "warning" },
  PAID: { label: "Paid", variant: "success" },
  VOID: { label: "Void", variant: "destructive" },
};

/** "WRITTEN_OFF" → "Written Off" — a status the backend added before this map knew it. */
function humanise(status: string): string {
  return status
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

interface StatusBadgeProps {
  status: InvoiceStatus;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  // Defensive: an unknown status must not crash the fee-note list/detail/client tab.
  const badge = STATUS_BADGE[status] ?? { label: humanise(String(status)), variant: "neutral" };
  return <Badge variant={badge.variant}>{badge.label}</Badge>;
}
