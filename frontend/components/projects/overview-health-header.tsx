import { HealthBadge } from "@/components/dashboard/health-badge";
import { cn } from "@/lib/utils";
import type { ProjectHealthDetail } from "@/lib/dashboard-types";

interface OverviewHealthHeaderProps {
  health: ProjectHealthDetail | null;
  projectName: string;
  customerName: string | null;
}

const REASON_BADGE_COLORS: Record<string, string> = {
  budget: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  overdue: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  default: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
};

function getReasonBadgeColor(reason: string): string {
  const lower = reason.toLowerCase();
  if (lower.includes("budget")) return REASON_BADGE_COLORS.budget;
  if (lower.includes("overdue")) return REASON_BADGE_COLORS.overdue;
  return REASON_BADGE_COLORS.default;
}

export function OverviewHealthHeader({
  health,
  projectName,
  customerName,
}: OverviewHealthHeaderProps) {
  const status = health?.healthStatus ?? "UNKNOWN";
  const reasons = health?.healthReasons ?? [];

  return (
    <div className="flex items-start gap-4">
      <HealthBadge status={status} size="lg" />
      <div className="min-w-0 flex-1">
        <h2 className="font-display text-lg text-slate-950 dark:text-slate-50">
          {projectName}
        </h2>
        {customerName && (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Customer: {customerName}
          </p>
        )}
        {reasons.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {reasons.map((reason, i) => (
              <span
                key={i}
                className={cn(
                  "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium",
                  getReasonBadgeColor(reason)
                )}
              >
                {reason}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
