import { todayInSA } from "@b2mash/shared/date";
import { addDays, mondayOf } from "@/app/(app)/org/[slug]/diary/diary-types";

/**
 * Resolves date range from search params, defaulting to the current South African week
 * (Monday to Sunday). The SSR server runs in UTC, so "this week" is derived from the SA calendar
 * date, never the process zone (G88-F49).
 */
export function resolveMyWorkDateRange(
  searchParams: { from?: string; to?: string },
  now: Date = new Date()
): {
  from: string;
  to: string;
} {
  if (searchParams.from && searchParams.to) {
    return { from: searchParams.from, to: searchParams.to };
  }

  const monday = mondayOf(todayInSA(now));
  return { from: monday, to: addDays(monday, 6) };
}
