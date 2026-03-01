import { cn } from "@/lib/utils";
import { statusBadgeVariants } from "@/components/ui/status-badge";

const PORTAL_STATUS_LABELS: Record<string, string> = {
  SENT: "Pending",
  ACCEPTED: "Accepted",
  DECLINED: "Declined",
  EXPIRED: "Expired",
};

const STATUS_VARIANT_MAP: Record<string, "blue" | "emerald" | "red" | "amber"> =
  {
    SENT: "blue",
    ACCEPTED: "emerald",
    DECLINED: "red",
    EXPIRED: "amber",
  };

interface PortalProposalStatusBadgeProps {
  status: string;
  className?: string;
}

export function PortalProposalStatusBadge({
  status,
  className,
}: PortalProposalStatusBadgeProps) {
  const normalized = status.trim().toUpperCase();
  const label = PORTAL_STATUS_LABELS[normalized] ?? normalized;
  const variant = STATUS_VARIANT_MAP[normalized] ?? "slate";

  return (
    <span className={cn(statusBadgeVariants({ variant }), className)}>
      {label}
    </span>
  );
}
