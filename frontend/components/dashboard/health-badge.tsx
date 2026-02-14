import { cn } from "@/lib/utils";

interface HealthBadgeProps {
  status: "HEALTHY" | "AT_RISK" | "CRITICAL" | "UNKNOWN";
  reasons?: string[] | null;
  size?: "sm" | "md" | "lg";
}

const STATUS_COLORS: Record<HealthBadgeProps["status"], string> = {
  HEALTHY: "bg-green-500",
  AT_RISK: "bg-amber-500",
  CRITICAL: "bg-red-500",
  UNKNOWN: "bg-gray-400",
};

const STATUS_LABELS: Record<HealthBadgeProps["status"], string> = {
  HEALTHY: "Healthy",
  AT_RISK: "At Risk",
  CRITICAL: "Critical",
  UNKNOWN: "Unknown",
};

const STATUS_TEXT_COLORS: Record<HealthBadgeProps["status"], string> = {
  HEALTHY: "text-green-700 dark:text-green-400",
  AT_RISK: "text-amber-700 dark:text-amber-400",
  CRITICAL: "text-red-700 dark:text-red-400",
  UNKNOWN: "text-gray-500 dark:text-gray-400",
};

export function HealthBadge({ status, reasons, size = "md" }: HealthBadgeProps) {
  const label = STATUS_LABELS[status];
  const tooltipText =
    reasons && reasons.length > 0 ? reasons.join("; ") : undefined;

  if (size === "sm") {
    return (
      <span
        className={cn("inline-block size-2 rounded-full", STATUS_COLORS[status])}
        title={tooltipText}
        aria-label={label}
      />
    );
  }

  if (size === "md") {
    return (
      <span
        className="inline-flex items-center gap-1.5"
        title={tooltipText}
      >
        <span
          className={cn(
            "inline-block size-2 rounded-full",
            STATUS_COLORS[status]
          )}
          aria-hidden="true"
        />
        <span
          className={cn("text-sm font-medium", STATUS_TEXT_COLORS[status])}
        >
          {label}
        </span>
      </span>
    );
  }

  // lg size
  return (
    <div className="flex flex-col gap-1">
      <span className="inline-flex items-center gap-1.5">
        <span
          className={cn(
            "inline-block size-2 rounded-full",
            STATUS_COLORS[status]
          )}
          aria-hidden="true"
        />
        <span
          className={cn("text-sm font-medium", STATUS_TEXT_COLORS[status])}
        >
          {label}
        </span>
      </span>
      {reasons && reasons.length > 0 && (
        <ul className="ml-3.5 space-y-0.5">
          {reasons.map((reason, i) => (
            <li
              key={i}
              className="text-xs text-muted-foreground"
            >
              {reason}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
