"use client";

import { useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { CalendarDays, CalendarX2, SearchX } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CreateCourtDateDialog } from "@/components/legal/create-court-date-dialog";
import {
  DiaryFilterRow,
  FEE_EARNER_ALL,
  FEE_EARNER_MINE,
} from "@/components/diary/diary-filter-row";
import { DiaryListView } from "@/components/diary/diary-list-view";
import { DiaryWeekView } from "@/components/diary/diary-week-view";
import {
  filterDiaryEvents,
  parseDiaryView,
  parseIsoDate,
  partitionOverdueBand,
  type DiaryEventResponse,
  type DiaryKind,
  type DiaryMatterOption,
  type DiaryMemberOption,
  type DiaryView,
} from "./diary-types";

export interface DiaryPageClientProps {
  slug: string;
  /** Server "today" (yyyy-MM-dd) — computed once on the server to avoid SSR/CSR drift. */
  today: string;
  /** Monday of the loaded week. */
  from: string;
  /** Sunday of the loaded week. */
  to: string;
  initialView: DiaryView;
  /** Kind chips preselected from `?types=` (empty = all served kinds). */
  initialKinds: DiaryKind[];
  events: DiaryEventResponse[];
  typesServed: DiaryKind[];
  loadError: boolean;
  members: DiaryMemberOption[];
  matters: DiaryMatterOption[];
  currentMemberId: string | null;
  isAdminOrOwner: boolean;
}

function sameKinds(a: DiaryKind[], b: DiaryKind[]): boolean {
  return a.length === b.length && a.every((k) => b.includes(k));
}

function weekLabel(from: string, to: string): string {
  const start = parseIsoDate(from).toLocaleDateString("en-GB", { day: "numeric", month: "long" });
  const end = parseIsoDate(to).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
  return `${start} to ${end}`;
}

/**
 * Diary view switch, filters and empty states. View and filter changes are client-only state —
 * the URL is kept shareable with history.replaceState so no server round trip happens.
 */
export function DiaryPageClient({
  slug,
  today,
  from,
  to,
  initialView,
  initialKinds,
  events,
  typesServed,
  loadError,
  members,
  matters,
  currentMemberId,
  isAdminOrOwner,
}: DiaryPageClientProps) {
  const router = useRouter();
  const pathname = usePathname();

  const preselected = initialKinds.filter((k) => typesServed.includes(k));
  const allServed = typesServed;
  const showMine = currentMemberId !== null;

  const [view, setView] = useState<DiaryView>(initialView);
  const [kinds, setKinds] = useState<DiaryKind[]>(preselected.length > 0 ? preselected : allServed);
  const [feeEarner, setFeeEarner] = useState<string>(
    !isAdminOrOwner && currentMemberId ? FEE_EARNER_MINE : FEE_EARNER_ALL
  );
  const [matterId, setMatterId] = useState<string | null>(null);

  function syncUrl(nextView: DiaryView, nextKinds: DiaryKind[]) {
    const qs = new URLSearchParams();
    qs.set("from", from);
    if (nextView !== "week") qs.set("view", nextView);
    if (!sameKinds(nextKinds, allServed)) qs.set("types", nextKinds.join(","));
    window.history.replaceState(null, "", `${pathname}?${qs.toString()}`);
  }

  function handleViewChange(value: string) {
    const next = parseDiaryView(value);
    setView(next);
    syncUrl(next, kinds);
  }

  function handleKindsChange(next: DiaryKind[]) {
    setKinds(next);
    syncUrl(view, next);
  }

  function clearFilters() {
    setKinds(allServed);
    setFeeEarner(FEE_EARNER_ALL);
    setMatterId(null);
    syncUrl(view, allServed);
  }

  if (loadError) {
    return (
      <EmptyState
        icon={CalendarX2}
        title="Unable to load the diary"
        description="The diary could not be loaded. Refresh the page to try again."
      />
    );
  }

  const feeEarnerId =
    feeEarner === FEE_EARNER_MINE
      ? currentMemberId
      : feeEarner === FEE_EARNER_ALL
        ? null
        : feeEarner;
  // A 7-day window is always filtered client-side (shouldUseServerFeeEarnerFilter is false).
  const filtered = filterDiaryEvents(events, { kinds, feeEarnerId, matterId });
  const { band, inWindow } = partitionOverdueBand(filtered, from);
  const courtServed = typesServed.includes("COURT_DATE");

  let body: React.ReactNode;
  if (events.length === 0) {
    body = (
      <EmptyState
        icon={CalendarDays}
        title="Nothing in the diary for this week."
        description={
          courtServed
            ? "Court dates, prescription dates, matter due dates and task due dates appear here."
            : "Matter due dates and task due dates appear here."
        }
        action={
          courtServed ? (
            <CreateCourtDateDialog
              slug={slug}
              triggerLabel="Add a court date"
              onSuccess={() => router.refresh()}
            />
          ) : undefined
        }
      />
    );
  } else if (filtered.length === 0) {
    body = (
      <EmptyState
        icon={SearchX}
        title="No events match these filters."
        description="Change the filters or clear them to see the whole week."
        actionLabel="Clear filters"
        onAction={clearFilters}
      />
    );
  } else if (view === "list") {
    body = <DiaryListView events={inWindow} band={band} slug={slug} />;
  } else {
    body = <DiaryWeekView events={inWindow} band={band} from={from} today={today} slug={slug} />;
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Tabs value={view} onValueChange={handleViewChange}>
          <TabsList aria-label="Diary view">
            <TabsTrigger value="week">Week</TabsTrigger>
            <TabsTrigger value="list">List</TabsTrigger>
          </TabsList>
        </Tabs>
        <p className="text-sm text-slate-600 dark:text-slate-400" data-testid="diary-range">
          {weekLabel(from, to)}
        </p>
      </div>

      <DiaryFilterRow
        typesServed={typesServed}
        kinds={kinds}
        onKindsChange={handleKindsChange}
        feeEarner={feeEarner}
        onFeeEarnerChange={setFeeEarner}
        members={members}
        showMine={showMine}
        matterId={matterId}
        onMatterChange={setMatterId}
        matters={matters}
      />

      {body}
    </div>
  );
}
