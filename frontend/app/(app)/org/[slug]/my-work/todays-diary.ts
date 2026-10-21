import { unstable_rethrow } from "next/navigation";
import { ApiError, resolveServerReturnTo } from "@/lib/api/client";
import { redirectToReLogin } from "@/lib/auth/expiry";
import { getDiary } from "@/app/(app)/org/[slug]/diary/diary-actions";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

/**
 * My work's "Today's diary" (Phase 87 613D, §12.6.3): GET /api/diary?from=today&to=today — today's
 * rows plus the open-overdue band — scoped to the current user for every role, because My work is
 * personal (fee earner = task assignee, else matter lead). Without a resolved member there is no
 * "me" to scope to, so it returns `null` (the agenda's inline error) rather than the firm's diary.
 *
 * Every failure is non-fatal (`null`) except an expired session: a 401 goes to the branded
 * re-login exactly like the Diary's loader, and getDiary's own re-login redirect propagates.
 */
export async function loadTodaysDiary(
  today: string,
  currentMemberId: string
): Promise<DiaryEventResponse[] | null> {
  if (!currentMemberId) return null;
  try {
    const diary = await getDiary({ from: today, to: today, feeEarnerId: currentMemberId });
    return diary.events;
  } catch (error) {
    unstable_rethrow(error);
    if (error instanceof ApiError && error.status === 401) {
      redirectToReLogin(await resolveServerReturnTo(), "expired");
    }
    console.error("Failed to load today's diary:", error);
    return null;
  }
}
