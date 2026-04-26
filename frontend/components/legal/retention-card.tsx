import Link from "next/link";
import { ShieldAlert } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatLocalDate } from "@/lib/format";

export interface RetentionCardProps {
  /** Project status — card only renders when this is "CLOSED". */
  status: string;
  /**
   * ISO instant when the retention clock was anchored (ADR-249). Set on the first
   * ACTIVE→COMPLETED or ACTIVE→CLOSED transition; preserved across reopens.
   */
  retentionClockStartedAt: string | null;
  /**
   * Server-computed YYYY-MM-DD end date for the retention period
   * (`retentionClockStartedAt + orgSettings.legalMatterRetentionYears`). Null when
   * the org's retention years setting isn't configured — in which case the card
   * is hidden because there is nothing meaningful to display.
   */
  retentionEndsOn: string | null;
  /** Org slug used to deep-link into the data-protection settings page. */
  slug: string;
}

/**
 * Returns the number of whole days between today (UTC midnight) and the given
 * YYYY-MM-DD retention end date. Negative values are clamped to 0 — once the
 * retention window has elapsed the matter is queued for permanent deletion.
 */
function daysRemainingUntil(yyyyMmDd: string): number {
  const [year, month, day] = yyyyMmDd.split("-").map(Number);
  const endUtc = Date.UTC(year, month - 1, day);
  const now = new Date();
  const todayUtc = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const diffMs = endUtc - todayUtc;
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
  return Math.max(0, diffDays);
}

/**
 * Per-matter retention card (GAP-OBS-Day60-RetentionShape, slice E6.2).
 *
 * Surfaces the firm's retention policy as it applies to *this* closed matter so
 * staff don't have to look up `legalMatterRetentionYears` and do mental math.
 * Renders nothing when the matter isn't CLOSED, the clock isn't stamped, or the
 * server hasn't computed an end date (org's retention years unconfigured).
 */
export function RetentionCard({
  status,
  retentionClockStartedAt,
  retentionEndsOn,
  slug,
}: RetentionCardProps) {
  if (status !== "CLOSED" || retentionClockStartedAt == null) {
    return null;
  }
  if (retentionEndsOn == null) {
    return null;
  }

  const daysRemaining = daysRemainingUntil(retentionEndsOn);
  const formattedEndDate = formatLocalDate(retentionEndsOn);
  const remainingLabel =
    daysRemaining === 0 ? "0 days — pending deletion" : `${daysRemaining} days remaining`;

  return (
    <Card data-testid="retention-card">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <ShieldAlert className="size-4 text-amber-600 dark:text-amber-400" aria-hidden="true" />
          Retention period
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2 text-sm text-slate-600 dark:text-slate-400">
        <p>
          This closed matter will be permanently deleted on{" "}
          <strong className="text-slate-900 dark:text-slate-100">{formattedEndDate}</strong> (
          <span data-testid="retention-card-days-remaining">{remainingLabel}</span>).
        </p>
        <Link
          href={`/org/${slug}/settings/data-protection`}
          className="inline-flex text-xs font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
        >
          View data-protection settings
        </Link>
      </CardContent>
    </Card>
  );
}
