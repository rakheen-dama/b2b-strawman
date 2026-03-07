import { Badge } from "@/components/ui/badge";
import type { ExecutionStatus } from "@/lib/api/automations";

const EXECUTION_STATUS_CONFIG: Record<
  ExecutionStatus,
  { label: string; variant: "success" | "destructive" | "neutral" | "warning" }
> = {
  TRIGGERED: { label: "Running", variant: "warning" },
  ACTIONS_COMPLETED: { label: "Completed", variant: "success" },
  ACTIONS_FAILED: { label: "Failed", variant: "destructive" },
  CONDITIONS_NOT_MET: { label: "Skipped", variant: "neutral" },
};

interface ExecutionStatusBadgeProps {
  status: ExecutionStatus;
}

export function ExecutionStatusBadge({ status }: ExecutionStatusBadgeProps) {
  const config = EXECUTION_STATUS_CONFIG[status] ?? {
    label: status,
    variant: "neutral" as const,
  };

  return <Badge variant={config.variant}>{config.label}</Badge>;
}
