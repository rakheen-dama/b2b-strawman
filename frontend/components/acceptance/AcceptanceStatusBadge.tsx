import { CheckCircle2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { AcceptanceStatus } from "@/lib/actions/acceptance-actions";

const STATUS_CONFIG: Record<
  AcceptanceStatus,
  {
    label: string;
    variant: "neutral" | "warning" | "lead" | "success";
    showIcon?: boolean;
  }
> = {
  PENDING: { label: "Pending", variant: "warning" },
  SENT: { label: "Awaiting Acceptance", variant: "warning" },
  VIEWED: { label: "Viewed", variant: "lead" },
  ACCEPTED: { label: "Accepted", variant: "success", showIcon: true },
  EXPIRED: { label: "Expired", variant: "neutral" },
  REVOKED: { label: "Revoked", variant: "neutral" },
};

interface AcceptanceStatusBadgeProps {
  status: AcceptanceStatus;
}

export function AcceptanceStatusBadge({ status }: AcceptanceStatusBadgeProps) {
  const config = STATUS_CONFIG[status];
  return (
    <Badge variant={config.variant}>
      {config.showIcon && <CheckCircle2 className="mr-1 size-3" />}
      {config.label}
    </Badge>
  );
}
