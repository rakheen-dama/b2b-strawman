import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { RetainerStatus } from "@/lib/api/retainers";

type BadgeVariant = "success" | "warning" | "neutral";

interface StatusConfig {
  label: string;
  variant: BadgeVariant;
}

const STATUS_CONFIG: Record<RetainerStatus, StatusConfig> = {
  ACTIVE: { label: "Active", variant: "success" },
  PAUSED: { label: "Paused", variant: "warning" },
  TERMINATED: { label: "Terminated", variant: "neutral" },
};

interface RetainerStatusBadgeProps {
  status: RetainerStatus;
  className?: string;
}

export function RetainerStatusBadge({
  status,
  className,
}: RetainerStatusBadgeProps) {
  const config = STATUS_CONFIG[status] ?? {
    label: status,
    variant: "neutral" as BadgeVariant,
  };

  return (
    <Badge variant={config.variant} className={cn(className)}>
      {config.label}
    </Badge>
  );
}
