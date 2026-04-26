import { ShieldAlert, ShieldCheck } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/format";

export type KycSummaryState = "unverified" | "pending" | "verified";

export interface KycSummary {
  state: KycSummaryState;
  provider?: string | null;
  verifiedAt?: string | null;
}

interface KycStatusBadgeProps {
  summary: KycSummary;
}

export function KycStatusBadge({ summary }: KycStatusBadgeProps) {
  if (summary.state === "verified") {
    const title =
      summary.provider && summary.verifiedAt
        ? `Verified via ${summary.provider} on ${formatDate(summary.verifiedAt)}`
        : "KYC verified";
    return (
      <Badge variant="success" title={title}>
        <ShieldCheck className="size-3" />
        KYC Verified
      </Badge>
    );
  }

  if (summary.state === "pending") {
    return (
      <Badge variant="warning" title="KYC verification pending">
        <ShieldAlert className="size-3" />
        KYC Pending
      </Badge>
    );
  }

  // Unverified — keep header uncluttered.
  return null;
}
