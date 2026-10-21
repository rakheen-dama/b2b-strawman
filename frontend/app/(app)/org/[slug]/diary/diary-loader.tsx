import { unstable_rethrow } from "next/navigation";
import { getCurrentUserEmail } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
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
 * Fetches the week; members / matters / capabilities failures are non-fatal. Lives outside
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
  for (const result of [diary, members, matters, caps, email]) {
    if (result.status === "rejected") unstable_rethrow(result.reason);
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
