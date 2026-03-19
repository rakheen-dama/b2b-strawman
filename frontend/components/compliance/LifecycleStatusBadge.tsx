import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { LifecycleStatus } from "@/lib/types";

type BadgeVariant = "success" | "warning" | "destructive" | "neutral";

interface StatusConfig {
  label: string;
  variant: BadgeVariant;
  className?: string;
}

const STATUS_CONFIG: Record<LifecycleStatus, StatusConfig> = {
  PROSPECT: { label: "Prospect", variant: "neutral" },
  ONBOARDING: {
    label: "Onboarding",
    variant: "neutral",
    className: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  },
  ACTIVE: { label: "Active", variant: "success" },
  DORMANT: { label: "Dormant", variant: "warning" },
  OFFBOARDING: { label: "Offboarding", variant: "warning" },
  OFFBOARDED: { label: "Offboarded", variant: "destructive" },
  ANONYMIZED: {
    label: "Anonymized",
    variant: "neutral" as BadgeVariant,
    className: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300 border border-slate-300 dark:border-slate-600",
  },
};

interface LifecycleStatusBadgeProps {
  status: LifecycleStatus;
  className?: string;
}

export function LifecycleStatusBadge({ status, className }: LifecycleStatusBadgeProps) {
  const config = STATUS_CONFIG[status] ?? { label: status, variant: "neutral" as BadgeVariant };

  return (
    <Badge variant={config.variant} className={cn(config.className, className)}>
      {config.label}
    </Badge>
  );
}
