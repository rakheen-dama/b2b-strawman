import { StatusBadge } from "@/components/ui/status-badge";
import type { ProposalStatus } from "@/app/(app)/org/[slug]/proposals/proposal-actions";

interface ProposalStatusBadgeProps {
  status: ProposalStatus;
  className?: string;
}

export function ProposalStatusBadge({
  status,
  className,
}: ProposalStatusBadgeProps) {
  return <StatusBadge status={status} className={className} />;
}
