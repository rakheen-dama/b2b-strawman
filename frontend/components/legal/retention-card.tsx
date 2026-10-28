import type { ReactNode } from "react";
import Link from "next/link";
import { ShieldAlert } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { formatLocalDate } from "@/lib/format";
import { daysUntil } from "@/lib/date-utils";
import { settingsHref } from "@/lib/settings-nav";

/** The seeded firm matter retention period (backend `DEFAULT_LEGAL_MATTER_RETENTION_YEARS`). */
const DEFAULT_RETENTION_YEARS = 5;

export interface RetentionCardProps {
  /** Matter status — the card renders for COMPLETED and CLOSED matters only. */
  status: string;
  /**
   * ISO instant of the matter's last close (ADR-354: stamped at every close, kept on reopen, so an
   * ACTIVE or COMPLETED matter can carry a stale one). Used only for the "Clock started on"
   * sentence and to derive the period a CLOSED matter's date was stamped with.
   */
  retentionClockStartedAt: string | null;
  /**
   * Persisted `YYYY-MM-DD` scheduled deletion date (close date + firm years, stamped at close and
   * cleared on reopen). Null unless the matter is CLOSED; there is no read-time computation.
   */
  retentionEndsOn: string | null;
  /** The firm's current matter retention period in years; null when the settings read failed. */
  firmRetentionYears: number | null;
  /** Org slug used to deep-link into the retention settings. */
  slug: string;
}

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * The retention period, in years, a CLOSED matter's deletion date was stamped with — derived from
 * its own dates so it stays true after the firm setting changes (the date is frozen, ADR-354).
 *
 * The backend stamps `retentionEndsOn = <UTC calendar date of the close> + years`, and the clock
 * is the close instant, so the clock's UTC month-day must match the end date's (29 Feb maps to
 * 28 Feb in a non-leap end year). Anything else — a missing clock, a legacy completion-time
 * clock, a malformed value — is not derivable and returns null.
 */
export function deriveRetentionYears(
  retentionClockStartedAt: string | null,
  retentionEndsOn: string | null
): number | null {
  if (retentionClockStartedAt == null || retentionEndsOn == null) return null;

  const clock = new Date(retentionClockStartedAt);
  const end = ISO_DATE.exec(retentionEndsOn);
  if (Number.isNaN(clock.getTime()) || end == null) return null;

  const clockYear = clock.getUTCFullYear();
  const clockMonth = clock.getUTCMonth() + 1;
  const clockDay = clock.getUTCDate();
  const endYear = Number(end[1]);
  const endMonth = Number(end[2]);
  const endDay = Number(end[3]);

  const sameMonthDay = clockMonth === endMonth && clockDay === endDay;
  const leapDayClamped = clockMonth === 2 && clockDay === 29 && endMonth === 2 && endDay === 28;
  if (!sameMonthDay && !leapDayClamped) return null;

  const years = endYear - clockYear;
  return years >= 1 ? years : null;
}

/** "(firm default 5 years)" only when both the matter's period and the firm setting are 5. */
function periodNote(matterYears: number | null, firmRetentionYears: number | null): string | null {
  if (matterYears == null) return null;
  if (matterYears === DEFAULT_RETENTION_YEARS && firmRetentionYears === DEFAULT_RETENTION_YEARS) {
    return `(firm default ${DEFAULT_RETENTION_YEARS} years)`;
  }
  return `(${matterYears}-year retention period)`;
}

const STRONG = "text-slate-900 dark:text-slate-100";
const LINK =
  "inline-flex text-xs font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300";

function RetentionCardShell({
  state,
  warn,
  children,
}: {
  state: string;
  warn: boolean;
  children: ReactNode;
}) {
  return (
    <Card data-testid="retention-card" data-state={state}>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <ShieldAlert
            className={warn ? "size-4 text-amber-600 dark:text-amber-400" : "size-4 text-slate-500"}
            aria-hidden="true"
          />
          Retention period
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2 text-sm text-slate-600 dark:text-slate-400">
        {children}
      </CardContent>
    </Card>
  );
}

/**
 * Per-matter retention card on the matter Overview (Phase 88 U17, ADR-354).
 *
 * Shows the matter's persisted scheduled deletion date. States:
 *  - `active` — CLOSED with a date: "Scheduled deletion: {date}" plus the period note, the
 *    clock sentence and days remaining, and a link to the retention settings.
 *  - `unscheduled` — CLOSED without a date: only a data error can cause this now (the setting
 *    always has a value), so it is surfaced rather than hidden.
 *  - `completed-pending-close` — COMPLETED: retention starts at close, so no date is shown,
 *    whatever clock the matter carries from an earlier close.
 *
 * Renders nothing for any other status (ACTIVE, including a reopened matter).
 */
export function RetentionCard({
  status,
  retentionClockStartedAt,
  retentionEndsOn,
  firmRetentionYears,
  slug,
}: RetentionCardProps) {
  if (status === "COMPLETED") {
    const period =
      firmRetentionYears == null ? null : (
        <>
          <strong className={STRONG}>
            {firmRetentionYears} {firmRetentionYears === 1 ? "year" : "years"}
          </strong>
          {firmRetentionYears === DEFAULT_RETENTION_YEARS ? " (firm default)" : ""}
        </>
      );
    return (
      <RetentionCardShell state="completed-pending-close" warn={false}>
        <p>
          Retention starts when this matter is closed. Closed matters are kept for{" "}
          {period ?? "your firm's retention period"} and then permanently deleted.
        </p>
      </RetentionCardShell>
    );
  }

  if (status !== "CLOSED") {
    return null;
  }

  const settingsLink = (
    <Link href={settingsHref(slug, "firm", "compliance")} className={LINK}>
      View retention settings
    </Link>
  );

  if (retentionEndsOn == null) {
    return (
      <RetentionCardShell state="unscheduled" warn>
        <p>No scheduled deletion date is recorded for this closed matter.</p>
        {settingsLink}
      </RetentionCardShell>
    );
  }

  const formattedEndDate = formatLocalDate(retentionEndsOn);
  // The clock is an ISO-UTC instant; its first 10 characters are the UTC close date.
  const clockStartedDate =
    retentionClockStartedAt != null ? formatLocalDate(retentionClockStartedAt.slice(0, 10)) : null;
  const note = periodNote(
    deriveRetentionYears(retentionClockStartedAt, retentionEndsOn),
    firmRetentionYears
  );
  const daysRemaining = daysUntil(retentionEndsOn);
  const remainingLabel =
    daysRemaining === 0 ? "0 days — pending deletion" : `${daysRemaining} days remaining`;

  return (
    <RetentionCardShell state="active" warn>
      <p data-testid="retention-card-scheduled-deletion">
        Scheduled deletion: <strong className={STRONG}>{formattedEndDate}</strong>
        {note ? ` ${note}` : null}
      </p>
      <p>
        {clockStartedDate != null && (
          <>
            Clock started on <strong className={STRONG}>{clockStartedDate}</strong>.{" "}
          </>
        )}
        This closed matter will be permanently deleted on{" "}
        <strong className={STRONG}>{formattedEndDate}</strong> (
        <span data-testid="retention-card-days-remaining">{remainingLabel}</span>).
      </p>
      {settingsLink}
    </RetentionCardShell>
  );
}
