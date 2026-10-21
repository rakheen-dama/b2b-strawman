import { Suspense } from "react";
import { unstable_rethrow } from "next/navigation";
import { getCurrentUserEmail } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { DiaryListSkeleton } from "@/components/diary/diary-list-view";
import { DiaryWeekSkeleton } from "@/components/diary/diary-week-view";
import { getDiary, getDiaryMatters, getDiaryMembers } from "./diary-actions";
import { DiaryPageClient } from "./diary-page-client";
import {
  addDays,
  isValidIsoDate,
  mondayOf,
  parseDiaryKinds,
  parseDiaryView,
  toIsoDate,
  type DiaryKind,
  type DiaryView,
} from "./diary-types";

/**
 * One Diary (Phase 87 613B) — court dates, prescription dates, matter due dates and task due
 * dates for one Mon–Sun week. `?from=` (any day → its Monday), `?view=week|list`, `?types=` (chip
 * preselection, applied client-side). Reachable by URL only until 613D adds the nav item.
 */
export default async function DiaryPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ view?: string; from?: string; types?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;

  const today = toIsoDate(new Date());
  const view = parseDiaryView(search.view);
  const from = mondayOf(isValidIsoDate(search.from) ? search.from : today);
  const to = addDays(from, 6);
  const initialKinds = parseDiaryKinds(search.types);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Diary</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Court dates, prescription dates, matter due dates and task due dates for the week.
        </p>
      </div>
      <Suspense
        key={from}
        fallback={view === "list" ? <DiaryListSkeleton /> : <DiaryWeekSkeleton />}
      >
        <DiaryLoader
          slug={slug}
          today={today}
          from={from}
          to={to}
          view={view}
          initialKinds={initialKinds}
        />
      </Suspense>
    </div>
  );
}

interface DiaryLoaderProps {
  slug: string;
  today: string;
  from: string;
  to: string;
  view: DiaryView;
  initialKinds: DiaryKind[];
}

/** Fetches the week; members / matters / capabilities failures are non-fatal. */
async function DiaryLoader({ slug, today, from, to, view, initialKinds }: DiaryLoaderProps) {
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
  const currentEmail = email.status === "fulfilled" ? email.value : null;
  const currentMemberId =
    (currentEmail && memberList.find((m) => m.email === currentEmail)?.id) || null;
  // Capabilities unknown → show everyone's events rather than silently narrowing to "Mine".
  const isAdminOrOwner =
    caps.status === "fulfilled" ? caps.value.isAdmin || caps.value.isOwner : true;

  return (
    <DiaryPageClient
      key={`${from}|${view}|${initialKinds.join(",")}`}
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
