import { unstable_rethrow } from "next/navigation";
import { getCurrentUserEmail } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError, resolveServerReturnTo } from "@/lib/api/client";
import { redirectToReLogin } from "@/lib/auth/expiry";
import { getDiary, getDiaryMatters, getDiaryMembers } from "./diary-actions";
import { DiaryPageClient } from "./diary-page-client";
import type { DiaryKind, DiaryMatterOption, DiaryMemberOption, DiaryView } from "./diary-types";

export interface DiaryLoaderProps {
  slug: string;
  today: string;
  from: string;
  to: string;
  view: DiaryView;
  /** `null` = `?types=` absent (every served kind); `[]` = present but empty (no kinds). */
  initialKinds: DiaryKind[] | null;
  /** Raw `?feeEarner=` (`mine` | `all` | member id); validated here, `null` = role default. */
  initialFeeEarner?: string | null;
  /** Raw `?matter=`; validated here, `null` = all matters. */
  initialMatterId?: string | null;
}

/** `?feeEarner=` survives only when it names something this page can offer. */
function validFeeEarner(
  value: string | null | undefined,
  members: DiaryMemberOption[],
  currentMemberId: string | null
): string | null {
  if (!value) return null;
  if (value === "all") return value;
  if (value === "mine") return currentMemberId ? value : null;
  return members.some((m) => m.id === value) ? value : null;
}

function validMatterId(
  value: string | null | undefined,
  matters: DiaryMatterOption[]
): string | null {
  return value && matters.some((m) => m.id === value) ? value : null;
}

/**
 * Fetches the window; failures other than an expired-session 401 are non-fatal. Lives outside
 * page.tsx because a Next.js page file may only export the page and route config.
 */
export async function DiaryLoader({
  slug,
  today,
  from,
  to,
  view,
  initialKinds,
  initialFeeEarner = null,
  initialMatterId = null,
}: DiaryLoaderProps) {
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
  // Capabilities unknown → show everyone's events rather than silently narrowing to "Mine". This
  // fail-open default seeds the filter only; it must never grant a write.
  const isAdminOrOwner =
    caps.status === "fulfilled" ? caps.value.isAdmin || caps.value.isOwner : true;
  // Marking someone else's task done needs a known admin / owner (TaskService.completeTask), so
  // it fails closed: without capabilities only the assignee's own tasks offer Mark done.
  const canCompleteAnyTask =
    caps.status === "fulfilled" && (caps.value.isAdmin || caps.value.isOwner);
  // Write affordances fail closed: court-date / prescription writes need MANAGE_LEGAL (backend
  // @RequiresCapability), so without known capabilities they are hidden.
  const canManageLegal =
    caps.status === "fulfilled" &&
    (caps.value.isAdmin || caps.value.isOwner || caps.value.capabilities.includes("MANAGE_LEGAL"));
  const matterList = matters.status === "fulfilled" ? matters.value : [];

  return (
    <DiaryPageClient
      // Keyed on the data window only: view / filter changes inside a window rewrite the URL via
      // replaceState, so a router.refresh() re-renders with new searchParams — that must not
      // remount and drop the live filters. `to` is part of the key: Week → Day on a Monday keeps
      // `from` but loads a different window.
      key={`${from}_${to}`}
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
      matters={matterList}
      currentMemberId={currentMemberId}
      isAdminOrOwner={isAdminOrOwner}
      canCompleteAnyTask={canCompleteAnyTask}
      canManageLegal={canManageLegal}
      initialFeeEarner={validFeeEarner(initialFeeEarner, memberList, currentMemberId)}
      initialMatterId={validMatterId(initialMatterId, matterList)}
    />
  );
}
