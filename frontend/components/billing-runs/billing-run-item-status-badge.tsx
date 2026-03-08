import { Badge } from "@/components/ui/badge";
import type { BillingRunItemStatus } from "@/lib/api/billing-runs";

const STATUS_BADGE: Record<
  BillingRunItemStatus,
  {
    label: string;
    variant: "neutral" | "warning" | "success" | "destructive" | "secondary";
  }
> = {
  PENDING: { label: "Pending", variant: "neutral" },
  GENERATING: { label: "Generating", variant: "warning" },
  GENERATED: { label: "Generated", variant: "success" },
  FAILED: { label: "Failed", variant: "destructive" },
  EXCLUDED: { label: "Excluded", variant: "secondary" },
  CANCELLED: { label: "Cancelled", variant: "neutral" },
};

interface BillingRunItemStatusBadgeProps {
  status: BillingRunItemStatus;
}

export function BillingRunItemStatusBadge({
  status,
}: BillingRunItemStatusBadgeProps) {
  const badge = STATUS_BADGE[status];
  return <Badge variant={badge.variant}>{badge.label}</Badge>;
}
