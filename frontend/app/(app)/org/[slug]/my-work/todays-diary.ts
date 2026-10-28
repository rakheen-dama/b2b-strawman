import { rethrowOnExpiredSession } from "@/lib/auth/expiry-server";
import { getDiary } from "@/app/(app)/org/[slug]/diary/diary-actions";
import {
  addDays,
  UPCOMING_DIARY_DAYS,
  type DiaryEventResponse,
} from "@/app/(app)/org/[slug]/diary/diary-types";

/**
 * A GET /api/diary result for the current user — Today's diary or the Upcoming window.
 * `"unidentified"` = the signed-in user couldn't be matched to a member, so there is no personal
 * diary to show; `null` = the diary could not be loaded.
 */
export type DiaryEventsResult = DiaryEventResponse[] | "unidentified" | null;

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
): Promise<DiaryEventsResult> {
  // Firm-wide → no filter; otherwise the caller's own diary, or "unidentified" without a member.
  const scope = options.firmWide ? {} : currentMemberId ? { feeEarnerId: currentMemberId } : null;
  if (!scope) return "unidentified";
  try {
    const diary = await getDiary({ from: today, to: today, ...scope });
    return diary.events;
  } catch (error) {
    await rethrowOnExpiredSession(error);
    console.error("Failed to load today's diary:", error);
    return null;
  }
}

/**
 * My work's "Upcoming" list and "Next deadline" (Phase 88 G-D5): GET /api/diary from tomorrow
 * through today + 14, without the overdue band (Today's diary already shows it), scoped to the
 * current user — fee earner = task assignee, else the matter lead, so a matter lead sees that
 * matter's court dates. The backend takes a member id, not "me"; without a resolved member there
 * is no personal diary, so it returns `"unidentified"` and never falls back to the firm's diary.
 * Server order (date, then court dates first within a day) is kept as is.
 *
 * Fails soft like loadTodaysDiary: an expired-session 401 goes to the branded re-login, any other
 * error returns `null`.
 */
export async function loadUpcomingDiary(
  today: string,
  currentMemberId: string | null
): Promise<DiaryEventsResult> {
  if (!currentMemberId) return "unidentified";
  try {
    const diary = await getDiary({
      from: addDays(today, 1),
      to: addDays(today, UPCOMING_DIARY_DAYS),
      feeEarnerId: currentMemberId,
      includeOverdue: false,
    });
    return diary.events;
  } catch (error) {
    await rethrowOnExpiredSession(error);
    console.error("Failed to load the upcoming diary:", error);
    return null;
  }
}
