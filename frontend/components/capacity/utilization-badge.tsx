import { Badge } from "@/components/ui/badge";

interface UtilizationBadgeProps {
  percentage: number | null;
}

export function UtilizationBadge({ percentage }: UtilizationBadgeProps) {
  if (percentage === null) {
    return (
      <Badge variant="neutral" className="font-mono text-xs tabular-nums">
        --
      </Badge>
    );
  }

  let variant: "success" | "warning" | "destructive";
  if (percentage > 100) {
    variant = "destructive";
  } else if (percentage >= 80) {
    variant = "warning";
  } else {
    variant = "success";
  }

  return (
    <Badge variant={variant} className="font-mono text-xs tabular-nums">
      {percentage}%
    </Badge>
  );
}
