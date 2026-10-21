import { Badge } from "@b2mash/ui/badge";
import type { CompletenessScore } from "@/lib/types";

type CompletenessBadgeProps = { className?: string } & (
  | { score: CompletenessScore; percentage?: never }
  /** A bare percentage (e.g. `ClientListRow.completenessPercent`); null = no required field (N/A). */
  | { percentage: number | null; score?: never }
);

export function CompletenessBadge({ score, percentage, className }: CompletenessBadgeProps) {
  const value = score ? (score.totalRequired === 0 ? null : score.percentage) : percentage;

  if (value == null) {
    return (
      <Badge variant="neutral" className={className}>
        N/A
      </Badge>
    );
  }

  const variant = value === 100 ? "success" : value >= 50 ? "warning" : "destructive";

  return (
    <Badge variant={variant} className={className}>
      {value}%
    </Badge>
  );
}
