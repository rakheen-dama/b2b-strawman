import { todayInSA } from "@b2mash/shared/date";

/**
 * Returns the number of whole days between South African today and the given
 * YYYY-MM-DD date string (G88-F49: the same answer on the UTC SSR server and in
 * any browser). Negative values are clamped to 0.
 */
export function daysUntil(isoDate: string): number {
  return Math.max(0, wholeDaysBetween(todayInSA(), isoDate));
}

/** Calendar-day difference of two yyyy-MM-dd dates (zone-free UTC arithmetic). */
function wholeDaysBetween(from: string, to: string): number {
  const [fy, fm, fd] = from.split("-").map(Number);
  const [ty, tm, td] = to.split("-").map(Number);
  return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86_400_000);
}

/**
 * Resolves date range from URL search params, defaulting to current month.
 */
export function resolveDateRange(searchParams: { from?: string; to?: string }): {
  from: string;
  to: string;
} {
  if (searchParams.from && searchParams.to) {
    return { from: searchParams.from, to: searchParams.to };
  }

  // Default to the current South African month's boundaries
  const today = todayInSA();
  const [y, m] = today.split("-").map(Number);
  const lastDay = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const monthPrefix = today.slice(0, 8);

  return {
    from: searchParams.from || `${monthPrefix}01`,
    to: searchParams.to || `${monthPrefix}${String(lastDay).padStart(2, "0")}`,
  };
}
