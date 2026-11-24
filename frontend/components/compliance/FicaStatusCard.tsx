import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { Badge } from "@b2mash/ui/badge";
import { ShieldCheck } from "lucide-react";
import { formatDate } from "@/lib/format";
import { ficaReviewReasonLabels, type FicaStatus } from "@/lib/types/fica";

/**
 * FICA onboarding status tile (GAP-L-46; checklist-first since Phase 88, ADR-348).
 *
 * The projection comes from `GET /api/clients/{clientId}/fica-status`: the
 * client's onboarding checklist decides first (`checklistInstanceId`), its
 * fica-onboarding-pack information requests are the fallback (`requestId`).
 * The card links to whichever decided — the client's FICA onboarding tab
 * (there is no per-instance route) or the information request. Phase 93 655B
 * (ADR-388): a take-on attestation decides last (`attestationId`) — ATTESTED,
 * or ATTESTED_REVIEW with its review reasons — and links to the client's
 * overview, where the attestation card lives.
 *
 * Passing `ficaStatus=null` renders a soft "unknown" state — used when
 * the upstream fetch fails.
 */
interface FicaStatusCardProps {
  ficaStatus: FicaStatus | null;
  /** Org slug, for the checklist / information-request links. */
  slug: string;
}

const LINK_CLASS =
  "mt-2 inline-block text-xs text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300";

const STATUS_BADGE: Record<
  FicaStatus["status"],
  { label: string; variant: "success" | "warning" | "neutral" }
> = {
  DONE: { label: "Done", variant: "success" },
  IN_PROGRESS: { label: "In Progress", variant: "warning" },
  NOT_STARTED: { label: "Not Started", variant: "neutral" },
  // §17.10 X3 wording (Phase 93 655B).
  ATTESTED: { label: "Verified in prior system (attested)", variant: "success" },
  ATTESTED_REVIEW: { label: "Review due (attested)", variant: "warning" },
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
  const attested = ficaStatus.status === "ATTESTED" || ficaStatus.status === "ATTESTED_REVIEW";
  const reasons =
    ficaStatus.status === "ATTESTED_REVIEW" ? ficaReviewReasonLabels(ficaStatus.reviewReasons) : [];
  // The checklist decided (ADR-348): its completion is "Completed", not "Verified".
  const fromChecklist = Boolean(ficaStatus.checklistInstanceId);

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
            {fromChecklist ? "Completed" : "Verified"}{" "}
            <span data-testid="fica-last-verified-at">{formatDate(ficaStatus.lastVerifiedAt)}</span>
          </p>
        )}
        {ficaStatus.status === "IN_PROGRESS" && (
          <p className="text-muted-foreground text-sm">
            {fromChecklist
              ? "Onboarding checklist in progress."
              : "Awaiting client response and firm-side review."}
          </p>
        )}
        {ficaStatus.status === "NOT_STARTED" && (
          <p className="text-muted-foreground text-sm">No FICA onboarding started.</p>
        )}
        {attested && (
          <p className="text-muted-foreground text-sm">CDD was done in the previous system.</p>
        )}
        {reasons.length > 0 && (
          <ul
            className="mt-1 list-disc space-y-0.5 pl-4 text-sm text-slate-700 dark:text-slate-300"
            data-testid="fica-review-reasons"
          >
            {reasons.map((label) => (
              <li key={label}>{label}</li>
            ))}
          </ul>
        )}
        {attested && ficaStatus.attestationId ? (
          <Link
            href={`/org/${slug}/clients/${ficaStatus.clientId}`}
            className={LINK_CLASS}
            data-testid="fica-attestation-link"
          >
            View attestation
          </Link>
        ) : fromChecklist ? (
          <Link
            href={`/org/${slug}/clients/${ficaStatus.clientId}?tab=onboarding`}
            className={LINK_CLASS}
            data-testid="fica-checklist-link"
          >
            View checklist
          </Link>
        ) : (
          ficaStatus.requestId && (
            <Link
              href={`/org/${slug}/information-requests/${ficaStatus.requestId}`}
              className={LINK_CLASS}
              data-testid="fica-request-link"
            >
              View request
            </Link>
          )
        )}
      </CardContent>
    </Card>
  );
}
