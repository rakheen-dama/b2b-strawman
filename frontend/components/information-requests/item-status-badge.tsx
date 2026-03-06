import { Badge } from "@/components/ui/badge";
import type { RequestItemStatus } from "@/lib/api/information-requests";

interface ItemStatusBadgeProps {
  status: RequestItemStatus;
}

const STATUS_CONFIG: Record<
  RequestItemStatus,
  { label: string; variant: "neutral" | "warning" | "success" | "destructive" }
> = {
  PENDING: { label: "Pending", variant: "neutral" },
  SUBMITTED: { label: "Submitted", variant: "warning" },
  ACCEPTED: { label: "Accepted", variant: "success" },
  REJECTED: { label: "Rejected", variant: "destructive" },
};

export function ItemStatusBadge({ status }: ItemStatusBadgeProps) {
  const config = STATUS_CONFIG[status];
  return <Badge variant={config.variant}>{config.label}</Badge>;
}
