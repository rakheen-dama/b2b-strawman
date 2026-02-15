import { Badge } from "@/components/ui/badge";
import type { InvoiceStatus } from "@/lib/types";

const STATUS_BADGE: Record<
  InvoiceStatus,
  { label: string; variant: "neutral" | "lead" | "success" | "destructive" }
> = {
  DRAFT: { label: "Draft", variant: "neutral" },
  APPROVED: { label: "Approved", variant: "lead" },
  SENT: { label: "Sent", variant: "lead" },
  PAID: { label: "Paid", variant: "success" },
  VOID: { label: "Void", variant: "destructive" },
};

interface StatusBadgeProps {
  status: InvoiceStatus;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const badge = STATUS_BADGE[status];
  return <Badge variant={badge.variant}>{badge.label}</Badge>;
}
