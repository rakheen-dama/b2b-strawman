import { Badge } from "@/components/ui/badge";
import type { LifecycleStatus } from "@/lib/types";

const LIFECYCLE_BADGE: Record<
  LifecycleStatus,
  { label: string; variant: "neutral" | "lead" | "success" | "warning" | "destructive" }
> = {
  PROSPECT: { label: "Prospect", variant: "neutral" },
  ONBOARDING: { label: "Onboarding", variant: "lead" },
  ACTIVE: { label: "Active", variant: "success" },
  DORMANT: { label: "Dormant", variant: "warning" },
  OFFBOARDED: { label: "Offboarded", variant: "destructive" },
};

interface LifecycleStatusBadgeProps {
  lifecycleStatus: LifecycleStatus;
}

export function LifecycleStatusBadge({ lifecycleStatus }: LifecycleStatusBadgeProps) {
  const badge = LIFECYCLE_BADGE[lifecycleStatus] ?? {
    label: lifecycleStatus,
    variant: "neutral" as const,
  };
  return <Badge variant={badge.variant}>{badge.label}</Badge>;
}
