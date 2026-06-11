/**
 * Unified formatting utilities shared by the staff app (frontend) and the
 * client portal.
 *
 * Convention (user-approved 2026-06-11): the customer-portal style wins
 * everywhere — en-GB day-first dates and en-ZA currency rendering. Previously
 * the two apps shipped divergent `format.ts` implementations (staff app:
 * per-currency locale map + en-US month-first dates; portal: always-en-ZA
 * currency + en-GB day-first dates). This module standardises on the portal
 * semantics and takes the superset of helpers from both files.
 *
 * NOTE ON ICU / @formatjs: this module does NOT import the @formatjs
 * NumberFormat polyfill. The staff app loads it once globally via
 * `@/lib/intl-polyfill` (Next.js Node ships small-icu, so en-ZA/en-GB would
 * otherwise silently fall back to en-US during SSR). The portal runs on
 * full-ICU runtimes and needs no polyfill. Because the polyfill is installed
 * process-wide before this module runs, the formatting calls below resolve the
 * correct locale data in both apps without this package depending on
 * @formatjs directly.
 */

// ---------------------------------------------------------------------------
// Currency
// ---------------------------------------------------------------------------

/**
 * Formats an amount as currency using the en-ZA locale (portal convention).
 * Defaults to ZAR. ZAR renders as "R 1,250.00"; USD as "US$18,750.00";
 * EUR as "€18,750.00" — i.e. the currency symbol is chosen by the currency
 * code while the number grouping/decimal style follows en-ZA.
 *
 * Robust against non-finite input ("R 0.00") and unknown currency codes
 * (falls back to "R <amount>").
 */
export function formatCurrency(amount: number, currency: string = "ZAR"): string {
  if (!Number.isFinite(amount)) return "R 0.00";

  try {
    return new Intl.NumberFormat("en-ZA", {
      style: "currency",
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    // Fallback for unknown currency codes
    return `R ${amount.toFixed(2)}`;
  }
}

/**
 * Null-safe currency formatter. Returns "N/A" if amount or currency is missing.
 * (Staff-app helper, retained; renders via the unified en-ZA `formatCurrency`.)
 */
export function formatCurrencySafe(
  amount: number | null | undefined,
  currency: string | null | undefined,
): string {
  if (amount == null || !currency) return "N/A";
  return formatCurrency(amount, currency);
}

// ---------------------------------------------------------------------------
// Dates
// ---------------------------------------------------------------------------

/**
 * Formats a date as a human-readable day-first date (en-GB: "1 May 2026").
 * Accepts an ISO string or a Date. Returns "" for empty/invalid input.
 */
export function formatDate(date: string | Date): string {
  if (!date) return "";
  const d = new Date(date);
  if (isNaN(d.getTime())) return "";
  return d.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/**
 * Formats a date as a short day-first date without year (en-GB: "1 May").
 * (Staff-app helper, retained; flipped from en-US month-first to en-GB.)
 */
export function formatDateShort(date: string | Date): string {
  if (!date) return "";
  const d = new Date(date);
  if (isNaN(d.getTime())) return "";
  return d.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
  });
}

/**
 * Formats a "YYYY-MM-DD" date string using local calendar arithmetic
 * (avoids UTC-midnight off-by-one issues). Renders en-ZA day-first
 * ("19 Feb 2026"). (Staff-app helper, retained.)
 */
export function formatLocalDate(yyyyMmDd: string): string {
  const [year, month, day] = yyyyMmDd.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

/**
 * Renders an ISO instant as a calendar date in the viewer's local zone.
 * Intended for proposal expiry / matter due-date fields where the underlying
 * value is a wall-clock end-of-day instant. (Staff-app helper, retained;
 * flipped from en-US month-first to en-GB day-first.)
 */
export function formatProposalExpiresAt(iso: string): string {
  return new Date(iso).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/**
 * Formats an ISO timestamp as a short date (en-ZA locale: "01 May 2026").
 * (Staff-app helper, retained.)
 */
export function formatComplianceDate(isoString: string): string {
  return new Date(isoString).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

/**
 * Formats an ISO timestamp with date and time (en-ZA locale).
 * (Staff-app helper, retained.)
 */
export function formatComplianceDateWithTime(isoString: string): string {
  return new Date(isoString).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * Returns true if the given "YYYY-MM-DD" deadline is strictly before today.
 * (Staff-app helper, retained.)
 */
export function isOverdue(deadline: string): boolean {
  const today = new Date().toLocaleDateString("en-CA"); // "YYYY-MM-DD" in local timezone
  return deadline < today;
}

/**
 * Formats a date as a relative time string (e.g., "2 hours ago", "just now").
 * Portal convention: a manual, dependency-free past-tense formatter. Accepts an
 * ISO string or a Date; returns "" for empty/invalid input.
 *
 * Note: this is the portal's algorithm (past-tense only). It supersedes the
 * staff app's previous Intl.RelativeTimeFormat implementation, which also
 * rendered future instants ("in 3 days"). The staff app only renders relative
 * dates for past audit/activity timestamps, so the visible behaviour is
 * unchanged in practice while keeping the portal output byte-identical.
 */
export function formatRelativeDate(date: string | Date): string {
  if (!date) return "";

  const parsed = new Date(date);
  if (isNaN(parsed.getTime())) return "";

  const now = new Date();
  const diffMs = now.getTime() - parsed.getTime();
  const diffSeconds = Math.floor(diffMs / 1000);
  const diffMinutes = Math.floor(diffSeconds / 60);
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);
  const diffMonths = Math.floor(diffDays / 30);
  const diffYears = Math.floor(diffDays / 365);

  if (diffYears > 0) return `${diffYears} year${diffYears > 1 ? "s" : ""} ago`;
  if (diffMonths > 0) return `${diffMonths} month${diffMonths > 1 ? "s" : ""} ago`;
  if (diffDays > 0) return `${diffDays} day${diffDays > 1 ? "s" : ""} ago`;
  if (diffHours > 0) return `${diffHours} hour${diffHours > 1 ? "s" : ""} ago`;
  if (diffMinutes > 0) return `${diffMinutes} minute${diffMinutes > 1 ? "s" : ""} ago`;
  return "just now";
}

// ---------------------------------------------------------------------------
// Sizes & durations
// ---------------------------------------------------------------------------

/**
 * Formats a byte count into a human-readable file size string.
 * Portal convention: 5 units (B, KB, MB, GB, TB) with one decimal place.
 * This supersedes the staff app's 4-unit ("B".."GB") version, which rendered
 * "5.0 undefined" for terabyte-scale inputs.
 */
export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return "0 B";
  if (bytes === 0) return "0 B";

  const units = ["B", "KB", "MB", "GB", "TB"];
  const k = 1024;
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const index = Math.min(i, units.length - 1);

  if (index === 0) return `${bytes} B`;

  const value = bytes / Math.pow(k, index);
  return `${value.toFixed(1)} ${units[index]}`;
}

/**
 * Formats a duration in minutes as "Xh Ym", "Xh", or "Ym".
 * Returns "0m" for zero or negative values. (Staff-app helper, retained.)
 */
export function formatDuration(minutes: number): string {
  if (minutes <= 0) return "0m";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h > 0 && m > 0) return `${h}h ${m}m`;
  if (h > 0) return `${h}h`;
  return `${m}m`;
}

/**
 * Formats the duration between two ISO timestamps as a human-readable string.
 * Returns "..." if completedAt is null (still running).
 * (Staff-app helper, retained.)
 */
export function computeDuration(startedAt: string, completedAt: string | null): string {
  if (!completedAt) return "...";
  const ms = new Date(completedAt).getTime() - new Date(startedAt).getTime();
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.round(ms / 60000)}m`;
}
