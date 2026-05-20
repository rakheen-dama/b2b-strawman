import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { SyncState } from "@/lib/types";

interface StateConfig {
  label: string;
  className: string;
}

const STATE_CONFIG: Record<SyncState, StateConfig> = {
  PENDING: {
    label: "Pending",
    className: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  },
  IN_FLIGHT: {
    label: "In Flight",
    className: "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
  },
  COMPLETED: {
    label: "Completed",
    className: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  },
  FAILED_RETRYING: {
    label: "Retrying",
    className: "bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300",
  },
  DEAD_LETTER: {
    label: "Dead Letter",
    className: "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300",
  },
  BLOCKED_TRUST_BOUNDARY: {
    label: "Blocked (Trust)",
    className: "bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300",
  },
  RECONCILE_DRIFT: {
    label: "Reconcile Drift",
    className: "bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300",
  },
};

interface SyncEntryStateBadgeProps {
  state: SyncState;
  className?: string;
}

export function SyncEntryStateBadge({ state, className }: SyncEntryStateBadgeProps) {
  const config = STATE_CONFIG[state] ?? {
    label: state,
    className: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
  };

  return (
    <Badge variant="neutral" className={cn(config.className, className)}>
      {config.label}
    </Badge>
  );
}
