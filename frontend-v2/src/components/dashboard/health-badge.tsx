import { cn } from "@/lib/utils";

type HealthStatus = "HEALTHY" | "AT_RISK" | "CRITICAL" | "UNKNOWN";

const statusConfig: Record<
  HealthStatus,
  { label: string; dot: string; text: string }
> = {
  HEALTHY: {
    label: "Healthy",
    dot: "bg-emerald-500",
    text: "text-emerald-700 dark:text-emerald-400",
  },
  AT_RISK: {
    label: "At Risk",
    dot: "bg-amber-500",
    text: "text-amber-700 dark:text-amber-400",
  },
  CRITICAL: {
    label: "Critical",
    dot: "bg-red-500",
    text: "text-red-700 dark:text-red-400",
  },
  UNKNOWN: {
    label: "Unknown",
    dot: "bg-slate-400",
    text: "text-slate-500 dark:text-slate-400",
  },
};

interface HealthBadgeProps {
  status: HealthStatus;
  className?: string;
}

export function HealthBadge({ status, className }: HealthBadgeProps) {
  const config = statusConfig[status];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 text-xs font-medium",
        config.text,
        className,
      )}
    >
      <span className={cn("size-2 rounded-full", config.dot)} />
      {config.label}
    </span>
  );
}
