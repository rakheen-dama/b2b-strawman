import { Badge } from "@/components/ui/badge";
import type { InformationRequestStatus } from "@/lib/api/information-requests";

interface RequestStatusBadgeProps {
  status: InformationRequestStatus;
}

const STATUS_CONFIG: Record<
  InformationRequestStatus,
  { label: string; variant?: "neutral" | "warning" | "success" | "destructive"; className?: string }
> = {
  DRAFT: { label: "Draft", variant: "neutral" },
  SENT: {
    label: "Sent",
    className: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  },
  IN_PROGRESS: { label: "In Progress", variant: "warning" },
  COMPLETED: { label: "Completed", variant: "success" },
  CANCELLED: { label: "Cancelled", variant: "destructive" },
};

export function RequestStatusBadge({ status }: RequestStatusBadgeProps) {
  const config = STATUS_CONFIG[status];

  if (config.className) {
    return <Badge className={config.className}>{config.label}</Badge>;
  }

  return <Badge variant={config.variant}>{config.label}</Badge>;
}
