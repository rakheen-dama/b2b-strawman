import { Badge } from "@/components/ui/badge";
import type { CompletenessScore } from "@/lib/types";

interface CompletenessBadgeProps {
  score: CompletenessScore;
  className?: string;
}

export function CompletenessBadge({ score, className }: CompletenessBadgeProps) {
  if (score.totalRequired === 0) {
    return (
      <Badge variant="neutral" className={className}>
        N/A
      </Badge>
    );
  }

  const variant =
    score.percentage === 100
      ? "success"
      : score.percentage >= 50
        ? "warning"
        : "destructive";

  return (
    <Badge variant={variant} className={className}>
      {score.percentage}%
    </Badge>
  );
}
