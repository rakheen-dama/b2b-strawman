import { portalGet } from "@/lib/api-client";

/**
 * Portal retainer API client.
 *
 * Backend (496A): routes live under `/portal/retainers/*`. Every endpoint is
 * module-gated server-side — if `retainer_agreements` is disabled for the
 * authenticated customer's tenant, the backend returns **404** (not 403).
 *
 * The controller also returns 400 if `from.isAfter(to)` and 404 when the
 * retainer is not owned by the caller's customer (enforced by the read-model
 * join on contact → customer → retainer).
 */

export type PortalRetainerPeriodType = "MONTHLY" | "QUARTERLY" | "ANNUAL";
export type PortalRetainerStatus = "ACTIVE" | "EXPIRED" | "PAUSED";

export interface PortalRetainerSummary {
  id: string;
  name: string;
  periodType: PortalRetainerPeriodType;
  /** BigDecimal serialised as a number via Jackson. */
  hoursAllotted: number;
  hoursConsumed: number;
  hoursRemaining: number;
  /** ISO 8601 local date "YYYY-MM-DD". */
  periodStart: string;
  periodEnd: string;
  rolloverHours: number;
  /** ISO 8601 local date "YYYY-MM-DD". */
  nextRenewalDate: string;
  status: PortalRetainerStatus;
}

export interface PortalRetainerConsumptionEntry {
  id: string;
  /** ISO 8601 local date "YYYY-MM-DD". */
  occurredAt: string;
  /** BigDecimal serialised as a number via Jackson. */
  hours: number;
  /** Sanitised server-side per ADR-254. */
  description: string;
  /** Resolved via `OrgSettings.portalRetainerMemberDisplay` per ADR-255. */
  memberDisplayName: string;
}

export interface ConsumptionRangeParams {
  /** ISO 8601 local date "YYYY-MM-DD", optional. */
  from?: string;
  /** ISO 8601 local date "YYYY-MM-DD", optional. */
  to?: string;
}

/**
 * Fetches active retainer summaries for the authenticated portal contact's
 * customer. Returns an empty array when the customer has no retainers but
 * throws when the `retainer_agreements` module is disabled (backend 404 →
 * `Error("The requested resource was not found.")`).
 */
export async function listRetainers(): Promise<PortalRetainerSummary[]> {
  return portalGet<PortalRetainerSummary[]>("/portal/retainers");
}

/**
 * Fetches consumption entries for a retainer, optionally bounded by a date
 * range. Both `from` and `to` are ISO-8601 dates ("YYYY-MM-DD"); omitting
 * either side leaves that side open-ended.
 *
 * Throws when the module is disabled, the retainer is not owned by the
 * caller's customer, or `from` is after `to` (backend 400).
 */
export async function getConsumption(
  retainerId: string,
  params: ConsumptionRangeParams = {},
): Promise<PortalRetainerConsumptionEntry[]> {
  const qs = new URLSearchParams();
  if (params.from) qs.set("from", params.from);
  if (params.to) qs.set("to", params.to);
  const query = qs.toString();
  const path = `/portal/retainers/${encodeURIComponent(retainerId)}/consumption${
    query ? `?${query}` : ""
  }`;
  return portalGet<PortalRetainerConsumptionEntry[]>(path);
}

/**
 * Formats a decimal hour count with two decimal places and an "h" suffix.
 * Used by retainer components for remaining/allotted/consumed figures.
 */
export function formatHours(n: number): string {
  if (!Number.isFinite(n)) return "0.00h";
  return `${n.toFixed(2)}h`;
}

/**
 * Computes the previous period bounds for a retainer, given the current
 * period start and period type. Returns ISO dates. Used when the user picks
 * "Previous period" in the consumption selector.
 *
 * Uses `subtractMonthsUTC` to safely handle month-end dates (e.g. subtracting
 * one month from `2026-03-31` yields `2026-02-28`, not `2026-03-03` as a naive
 * `Date.setUTCMonth()` would produce via day overflow).
 */
export function previousPeriodBounds(
  currentPeriodStart: string,
  periodType: PortalRetainerPeriodType,
): { from: string; to: string } {
  const start = new Date(`${currentPeriodStart}T00:00:00Z`);
  const prevEnd = new Date(start);
  prevEnd.setUTCDate(prevEnd.getUTCDate() - 1);
  let prevStart: Date;
  switch (periodType) {
    case "MONTHLY":
      prevStart = subtractMonthsUTC(start, 1);
      break;
    case "QUARTERLY":
      prevStart = subtractMonthsUTC(start, 3);
      break;
    case "ANNUAL":
      prevStart = subtractMonthsUTC(start, 12);
      break;
  }
  return { from: toIsoDate(prevStart), to: toIsoDate(prevEnd) };
}

/**
 * Subtracts `months` months from `date` (UTC) without day-overflow.
 *
 * `Date.setUTCMonth(m)` silently rolls over when the day-of-month does not
 * exist in the target month (e.g. Mar 31 minus 1 month → Mar 3, because Feb 28
 * + 3 overflow days). This helper builds the target via `Date.UTC(y, m, 1)`
 * and clamps the day to the last valid day of the target month, so
 * `Mar 31 − 1 month = Feb 28` (or Feb 29 in a leap year) as expected.
 */
function subtractMonthsUTC(date: Date, months: number): Date {
  const year = date.getUTCFullYear();
  const month = date.getUTCMonth();
  const day = date.getUTCDate();
  // Date.UTC handles year rollover for negative/out-of-range months.
  const firstOfTarget = new Date(Date.UTC(year, month - months, 1));
  const lastDayOfTarget = new Date(
    Date.UTC(
      firstOfTarget.getUTCFullYear(),
      firstOfTarget.getUTCMonth() + 1,
      0,
    ),
  ).getUTCDate();
  return new Date(
    Date.UTC(
      firstOfTarget.getUTCFullYear(),
      firstOfTarget.getUTCMonth(),
      Math.min(day, lastDayOfTarget),
    ),
  );
}

function toIsoDate(d: Date): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}
