import { unstable_rethrow } from "next/navigation";
import { getCurrentUserEmail } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError, resolveServerReturnTo } from "@/lib/api/client";
import { redirectToReLogin } from "@/lib/auth/expiry";
import { getDiary, getDiaryMatters, getDiaryMembers } from "./diary-actions";
import { DiaryPageClient } from "./diary-page-client";
import type { DiaryKind, DiaryView } from "./diary-types";

export interface DiaryLoaderProps {
  slug: string;
  today: string;
  from: string;
  to: string;
  view: DiaryView;
  /** `null` = `?types=` absent (every served kind); `[]` = present but empty (no kinds). */
  initialKinds: DiaryKind[] | null;
}

/**
 * Fetches the week; failures other than an expired-session 401 are non-fatal. Lives outside
 * page.tsx because a Next.js page file may only export the page and route config.
 */
export async function DiaryLoader({ slug, today, from, to, view, initialKinds }: DiaryLoaderProps) {
  const [diary, members, matters, caps, email] = await Promise.allSettled([
    // All kinds (no `types`): X-Diary-Types must report every kind this user may see; the
    // `?types=` selection is applied client-side.
    getDiary({ from, to }),
    getDiaryMembers(),
    getDiaryMatters(),
    fetchMyCapabilities(),
    getCurrentUserEmail(),
  ]);

  // Framework control flow (the re-login redirect on an expired session) must not be swallowed.
  const results = [diary, members, matters, caps, email];
  for (const result of results) {
    if (result.status === "rejected") unstable_rethrow(result.reason);
  }
  // A backend 401 (incl. the manual-redirect 3xx mapped to 401) is an expired session, not a
  // load failure: funnel to the branded re-login like apiRequest callers via handleApiError.
  if (
    results.some(
      (result) =>
        result.status === "rejected" &&
        result.reason instanceof ApiError &&
        result.reason.status === 401
    )
  ) {
    redirectToReLogin(await resolveServerReturnTo(), "expired");
  }
  if (diary.status === "rejected") console.error("Failed to load the diary:", diary.reason);
  if (members.status === "rejected") console.error("Failed to load members:", members.reason);
  if (matters.status === "rejected") console.error("Failed to load matters:", matters.reason);
  if (caps.status === "rejected") console.error("Failed to load capabilities:", caps.reason);
  if (email.status === "rejected") console.error("Failed to resolve current user:", email.reason);

  const memberList = members.status === "fulfilled" ? members.value : [];
  const currentEmail = email.status === "fulfilled" ? email.value?.toLowerCase() : null;
  const currentMemberId =
    (currentEmail && memberList.find((m) => m.email?.toLowerCase() === currentEmail)?.id) || null;
  // Capabilities unknown → show everyone's events rather than silently narrowing to "Mine".
  const isAdminOrOwner =
    caps.status === "fulfilled" ? caps.value.isAdmin || caps.value.isOwner : true;

  return (
    <DiaryPageClient
      // Keyed on the data window only: view / chip changes rewrite the URL via replaceState, so a
      // router.refresh() re-renders with new searchParams — that must not remount and drop the
      // fee-earner / matter filters.
      key={from}
      slug={slug}
      today={today}
      from={from}
      to={to}
      initialView={view}
      initialKinds={initialKinds}
      events={diary.status === "fulfilled" ? diary.value.events : []}
      typesServed={diary.status === "fulfilled" ? diary.value.typesServed : []}
      loadError={diary.status === "rejected"}
      members={memberList}
      matters={matters.status === "fulfilled" ? matters.value : []}
      currentMemberId={currentMemberId}
      isAdminOrOwner={isAdminOrOwner}
    />
  );
}
