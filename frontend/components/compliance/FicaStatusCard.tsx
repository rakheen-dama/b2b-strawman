import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ShieldCheck } from "lucide-react";
import { formatDate } from "@/lib/format";
import type { FicaStatus } from "@/lib/types/fica";

/**
 * FICA onboarding status tile (GAP-L-46).
 *
 * Info-request-only signal; no KYC-adapter coupling yet. The backend
 * projection comes from `GET /api/customers/{id}/fica-status` which
 * derives status from the customer's fica-onboarding-pack information
 * requests. When adapter outcomes and beneficial-owner coverage are
 * added later (separate phase) the server can upgrade the projection
 * without the UI contract changing.
 *
 * Passing `ficaStatus=null` renders a soft "unknown" state — used when
 * the upstream fetch fails. Passing `undefined` via optional prop is
 * treated the same way so the parent can pass the result of a
 * Promise.allSettled catch directly.
 */
interface FicaStatusCardProps {
  ficaStatus: FicaStatus | null;
  /** Org slug, for linking to the originating information-request. */
  slug: string;
}

const STATUS_BADGE: Record<
  FicaStatus["status"],
  { label: string; variant: "success" | "warning" | "neutral" }
> = {
  DONE: { label: "Done", variant: "success" },
  IN_PROGRESS: { label: "In Progress", variant: "warning" },
  NOT_STARTED: { label: "Not Started", variant: "neutral" },
};

export function FicaStatusCard({ ficaStatus, slug }: FicaStatusCardProps) {
  // Unknown — projection fetch failed upstream.
  if (!ficaStatus) {
    return (
      <Card data-testid="fica-status-card">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-1.5 text-sm font-medium">
            <ShieldCheck className="size-3.5 text-slate-400" />
            FICA
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm italic">Status unavailable</p>
        </CardContent>
      </Card>
    );
  }

  const badge = STATUS_BADGE[ficaStatus.status];

  return (
    <Card data-testid="fica-status-card">
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-1.5 text-sm font-medium">
            <ShieldCheck className="size-3.5 text-slate-400" />
            FICA
          </CardTitle>
          <Badge variant={badge.variant} data-testid="fica-status-badge">
            {badge.label}
          </Badge>
        </div>
      </CardHeader>
      <CardContent>
        {ficaStatus.status === "DONE" && ficaStatus.lastVerifiedAt && (
          <p className="text-sm text-slate-700 dark:text-slate-300">
            Verified{" "}
            <span data-testid="fica-last-verified-at">{formatDate(ficaStatus.lastVerifiedAt)}</span>
          </p>
        )}
        {ficaStatus.status === "IN_PROGRESS" && (
          <p className="text-muted-foreground text-sm">
            Awaiting client response and firm-side review.
          </p>
        )}
        {ficaStatus.status === "NOT_STARTED" && (
          <p className="text-muted-foreground text-sm">No FICA onboarding request yet.</p>
        )}
        {ficaStatus.requestId && (
          <Link
            href={`/org/${slug}/requests/${ficaStatus.requestId}`}
            className="mt-2 inline-block text-xs text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
            data-testid="fica-request-link"
          >
            View request
          </Link>
        )}
      </CardContent>
    </Card>
  );
}
