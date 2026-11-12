import { isoDateInSA } from "@b2mash/shared/date";
import type { TrendPoint } from "@/lib/dashboard-types";

export type DeltaUnit = "percent" | "points";

const DAY_MS = 86_400_000;

/**
 * Change of `current` against `previous` for a StatCard delta.
 * - percent → % change; `null` when either side is null or `previous` is 0 (no "∞%").
 * - points → `current − previous` when both are non-null.
 */
export function computeDelta(
  current: number | null | undefined,
  previous: number | null | undefined,
  unit: DeltaUnit
): number | null {
  if (current == null || previous == null) return null;
  if (!Number.isFinite(current) || !Number.isFinite(previous)) return null;
  if (unit === "points") return current - previous;
  if (previous === 0) return null;
  return ((current - previous) / Math.abs(previous)) * 100;
}

/** A "yyyy-MM-dd" string is a calendar date; a Date is mapped to its South African calendar date. */
function utcDate(date: string | Date): Date {
  const ymd = typeof date === "string" ? date.slice(0, 10) : isoDateInSA(date);
  const [y, m, d] = ymd.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

/** ISO week key of a UTC-midnight calendar date. */
function weekKeyOfUtc(day: Date): string {
  const t = new Date(day.getTime());
  const dow = (t.getUTCDay() + 6) % 7; // Mon=0 … Sun=6
  t.setUTCDate(t.getUTCDate() - dow + 3); // Thursday of this ISO week decides the ISO year
  const isoYear = t.getUTCFullYear();
  const jan4 = new Date(Date.UTC(isoYear, 0, 4)); // always in ISO week 1
  const week =
    1 +
    Math.round(((t.getTime() - jan4.getTime()) / DAY_MS - 3 + ((jan4.getUTCDay() + 6) % 7)) / 7);
  return `${isoYear}-W${String(week).padStart(2, "0")}`;
}

/**
 * ISO week key "IYYY-Www" (Monday start, zero-padded week), matching the backend trend's
 * Postgres `TO_CHAR(date_trunc('week', d), 'IYYY-"W"IW')`. Process-zone independent.
 */
export function isoWeekKey(date: string | Date): string {
  return weekKeyOfUtc(utcDate(date));
}

/**
 * The `n` weekly points ending at `to`'s ISO week, oldest first. Weeks missing from `trend`
 * become 0; keys outside the window are dropped (the backend's `to − 6 weeks … to` window can
 * return 7 weeks). Weekly ISO keys only — the 30-day dashboard window is weekly granularity.
 */
export function zeroFillTrend(
  trend: readonly TrendPoint[],
  to: string | Date,
  n = 6
): TrendPoint[] {
  const byKey = new Map(trend.map((point) => [point.period, point.value]));
  const end = utcDate(to).getTime();
  const points: TrendPoint[] = [];
  for (let i = n - 1; i >= 0; i--) {
    const period = weekKeyOfUtc(new Date(end - 7 * i * DAY_MS));
    points.push({ period, value: byKey.get(period) ?? 0 });
  }
  return points;
}
