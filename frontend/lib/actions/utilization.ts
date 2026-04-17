"use server";

import { getTeamUtilization, type TeamUtilizationResponse } from "@/lib/api/capacity";
import { getCurrentMonday, formatDate, addWeeks } from "@/lib/date-utils";

/**
 * Fetches the team billable-utilization trend for the last `weeks` weeks
 * (default 4), oldest first → newest last. Issues N sequential calls to
 * `GET /api/utilization/team` server-side, so the client makes a single
 * round-trip to the server action.
 *
 * The controller constraint is that `weekStart` must be a Monday — we use
 * `getCurrentMonday()` + `addWeeks()` to satisfy it.
 */
const MAX_WEEKS = 12;

export async function fetchTeamUtilizationTrend(
  weeks: number = 4
): Promise<TeamUtilizationResponse[]> {
  if (!Number.isInteger(weeks) || weeks < 1 || weeks > MAX_WEEKS) {
    throw new Error(`weeks must be an integer between 1 and ${MAX_WEEKS}`);
  }
  const monday = getCurrentMonday();
  const results: TeamUtilizationResponse[] = [];
  // i = weeks-1 is oldest, i = 0 is current week
  for (let i = weeks - 1; i >= 0; i--) {
    const weekStart = addWeeks(monday, -i);
    const weekEnd = addWeeks(weekStart, 1);
    const data = await getTeamUtilization(formatDate(weekStart), formatDate(weekEnd));
    results.push(data);
  }
  return results;
}
