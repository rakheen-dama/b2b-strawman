import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const PROPOSAL_STATUS_COLORS: Record<string, string> = {
  SENT: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  ACCEPTED:
    "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  DECLINED:
    "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
  EXPIRED:
    "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
};

interface ProposalStatusBadgeProps {
  status: string;
  className?: string;
}

function formatStatus(status: string): string {
  return status.replace(/_/g, " ");
}

export function ProposalStatusBadge({
  status,
  className,
}: ProposalStatusBadgeProps) {
  const colorClasses =
    PROPOSAL_STATUS_COLORS[status] ??
    PROPOSAL_STATUS_COLORS["DECLINED"] ??
    "";

  return (
    <Badge className={cn(colorClasses, className)}>
      {formatStatus(status)}
    </Badge>
  );
}
