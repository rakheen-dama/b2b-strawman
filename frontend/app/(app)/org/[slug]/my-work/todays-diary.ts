import { rethrowOnExpiredSession } from "@/lib/auth/expiry-server";
import { getDiary } from "@/app/(app)/org/[slug]/diary/diary-actions";
import type { TodaysDiaryEvents } from "@/components/dashboard/todays-agenda";

/**
 * My work's "Today's diary" (Phase 87 613D, §12.6.3): GET /api/diary?from=today&to=today — today's
 * rows plus the open-overdue band — scoped to the current user for every role, because My work is
 * personal (fee earner = task assignee, else matter lead). Without a resolved member there is no
 * "me" to scope to, so it returns `"unidentified"` (the agenda says so) rather than the firm's
 * diary.
 *
 * Home (614B) reuses it with `firmWide: true` for admins and owners — everyone's day, no fee-earner
 * filter, no member needed. Members never get the firm-wide fallback.
 *
 * Every failure is non-fatal (`null`) except an expired session: a 401 goes to the branded
 * re-login exactly like the Diary's loader, and getDiary's own re-login redirect propagates.
 */
export async function loadTodaysDiary(
  today: string,
  currentMemberId: string | null,
  options: { firmWide?: boolean } = {}
): Promise<TodaysDiaryEvents> {
  const firmWide = options.firmWide === true;
  if (!firmWide && !currentMemberId) return "unidentified";
  const scope = firmWide || !currentMemberId ? {} : { feeEarnerId: currentMemberId };
  try {
    const diary = await getDiary({ from: today, to: today, ...scope });
    return diary.events;
  } catch (error) {
    await rethrowOnExpiredSession(error);
    console.error("Failed to load today's diary:", error);
    return null;
  }
}
