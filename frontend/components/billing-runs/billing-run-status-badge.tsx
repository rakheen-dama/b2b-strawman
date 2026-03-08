import { Badge } from "@/components/ui/badge";
import type { BillingRunStatus } from "@/lib/api/billing-runs";

const STATUS_BADGE: Record<
  BillingRunStatus,
  { label: string; variant: "neutral" | "warning" | "success" | "destructive" }
> = {
  PREVIEW: { label: "Preview", variant: "neutral" },
  IN_PROGRESS: { label: "In Progress", variant: "warning" },
  COMPLETED: { label: "Completed", variant: "success" },
  CANCELLED: { label: "Cancelled", variant: "destructive" },
};

interface BillingRunStatusBadgeProps {
  status: BillingRunStatus;
}

export function BillingRunStatusBadge({ status }: BillingRunStatusBadgeProps) {
  const badge = STATUS_BADGE[status];
  return <Badge variant={badge.variant}>{badge.label}</Badge>;
}
