import { CheckCircle2 } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import type { AcceptanceStatus } from "@/lib/actions/acceptance-actions";

const STATUS_CONFIG: Record<
  AcceptanceStatus,
  {
    label: string;
    variant: "neutral" | "warning" | "lead" | "success" | "destructive";
    showIcon?: boolean;
  }
> = {
  PENDING: { label: "Pending", variant: "warning" },
  SENT: { label: "Awaiting Acceptance", variant: "warning" },
  VIEWED: { label: "Viewed", variant: "lead" },
  ACCEPTED: { label: "Accepted", variant: "success", showIcon: true },
  DECLINED: { label: "Declined", variant: "destructive" },
  EXPIRED: { label: "Expired", variant: "neutral" },
  REVOKED: { label: "Revoked", variant: "neutral" },
};

interface AcceptanceStatusBadgeProps {
  status: AcceptanceStatus;
  /** 624G (U23): shown as "Declined — {reason}" for DECLINED; ignored for other statuses. */
  declineReason?: string | null;
}

export function AcceptanceStatusBadge({ status, declineReason }: AcceptanceStatusBadgeProps) {
  const config = STATUS_CONFIG[status];
  const reason = status === "DECLINED" ? declineReason?.trim() : undefined;
  if (reason) {
    const label = `${config.label} — ${reason}`;
    // Long reasons (≤ 2000 chars) are truncated by CSS; the full text is in the title tooltip.
    return (
      <Badge variant={config.variant} className="max-w-[16rem]" title={label}>
        <span className="min-w-0 truncate">{label}</span>
      </Badge>
    );
  }
  return (
    <Badge variant={config.variant}>
      {config.showIcon && <CheckCircle2 className="mr-1 size-3" />}
      {config.label}
    </Badge>
  );
}
