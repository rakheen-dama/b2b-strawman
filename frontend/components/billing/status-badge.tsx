import { Badge } from "@/components/ui/badge";

const statusVariantMap: Record<
  string,
  "success" | "warning" | "destructive" | "neutral"
> = {
  ACTIVE: "success",
  GRACE_PERIOD: "warning",
  LOCKED: "destructive",
  EXPIRED: "neutral",
  PAST_DUE: "warning",
  PENDING_CANCELLATION: "warning",
  SUSPENDED: "destructive",
};

const statusLabelMap: Record<string, string> = {
  ACTIVE: "Active",
  TRIALING: "Trialing",
  GRACE_PERIOD: "Grace Period",
  LOCKED: "Locked",
  EXPIRED: "Expired",
  PAST_DUE: "Past Due",
  PENDING_CANCELLATION: "Pending Cancellation",
  SUSPENDED: "Suspended",
};

interface StatusBadgeProps {
  status: string;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  if (status === "TRIALING") {
    return (
      <Badge
        variant="neutral"
        className="bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300"
      >
        {statusLabelMap[status] ?? status}
      </Badge>
    );
  }

  const variant = statusVariantMap[status] ?? "neutral";
  return (
    <Badge variant={variant}>{statusLabelMap[status] ?? status}</Badge>
  );
}
