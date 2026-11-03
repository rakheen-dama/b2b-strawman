/**
 * Product-wide business calendar (G88-F48 / G88-F49). Every calendar date a user reasons about —
 * "today", due dates, deadlines, week and month ranges — is South African time, whatever zone the
 * runtime is in (Next.js SSR runs in UTC; a browser may run anywhere). Mirrors the backend's
 * `KaziTime.ZONE`.
 */
export const KAZI_TIME_ZONE = "Africa/Johannesburg";

// Year/month/day parts are locale-independent, so this stays correct under Node's small-icu
// (which may fall back to en-US for locale data) — unlike toLocaleDateString("en-CA").
const SA_DATE_PARTS = new Intl.DateTimeFormat("en-CA", {
  timeZone: KAZI_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** The yyyy-MM-dd South African calendar date of the instant `at`. */
export function isoDateInSA(at: Date): string {
  const parts = SA_DATE_PARTS.formatToParts(at);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? "";
  return `${part("year")}-${part("month")}-${part("day")}`;
}

/** Today's yyyy-MM-dd date in South Africa (default: at the current instant). */
export function todayInSA(now: Date = new Date()): string {
  return isoDateInSA(now);
}

/**
 * Whole calendar days from `from` to `to`, both yyyy-MM-dd (zone-free UTC arithmetic; the same
 * answer as the backend's `ChronoUnit.DAYS.between`). Negative when `to` is before `from`.
 */
export function daysBetween(from: string, to: string): number {
  const [fy, fm, fd] = from.split("-").map(Number);
  const [ty, tm, td] = to.split("-").map(Number);
  return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86_400_000);
}
