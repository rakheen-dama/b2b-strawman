"use client";

import { useState, useTransition } from "react";
import { usePathname, useRouter } from "next/navigation";
import { CalendarDays, CalendarX2, ChevronLeft, ChevronRight, SearchX } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { EmptyState } from "@/components/empty-state";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CancelCourtDateDialog } from "@/components/legal/cancel-court-date-dialog";
import { CreateCourtDateDialog } from "@/components/legal/create-court-date-dialog";
import { CreatePrescriptionDialog } from "@/components/legal/create-prescription-dialog";
import { OutcomeDialog } from "@/components/legal/outcome-dialog";
import { PostponeDialog } from "@/components/legal/postpone-dialog";
import { DiaryDayView } from "@/components/diary/diary-day-view";
import {
  DiaryFilterRow,
  FEE_EARNER_ALL,
  FEE_EARNER_MINE,
} from "@/components/diary/diary-filter-row";
import { DiaryListView } from "@/components/diary/diary-list-view";
import { DiaryMonthView } from "@/components/diary/diary-month-view";
import { DiaryActionsProvider, type DiaryRowAction } from "@/components/diary/diary-row-actions";
import { DiaryWeekView } from "@/components/diary/diary-week-view";
import { completeTask } from "@/app/(app)/org/[slug]/matters/[id]/task-actions";
import {
  DIARY_VIEWS,
  DIARY_VIEW_LABELS,
  DIARY_VIEW_UNIT,
  diaryWindow,
  filterDiaryEvents,
  formatDiaryRangeLabel,
  isValidIsoDate,
  parseDiaryView,
  partitionOverdueBand,
  sameWindow,
  stepDiaryWindow,
  windowContains,
  type DiaryEventResponse,
  type DiaryKind,
  type DiaryMatterOption,
  type DiaryMemberOption,
  type DiaryView,
  type DiaryWindow,
} from "./diary-types";

export interface DiaryPageClientProps {
  slug: string;
  /** Server "today" (yyyy-MM-dd) — computed once on the server to avoid SSR/CSR drift. */
  today: string;
  /** First day of the loaded window (day: the day; week: Monday; month / list: the 1st). */
  from: string;
  /** Last day of the loaded window, inclusive. */
  to: string;
  initialView: DiaryView;
  /**
   * Kind chips preselected from `?types=`: `null` = param absent (all served kinds); `[]` = present
   * but empty (no kinds). Requested kinds the server did not serve are dropped; if none of a
   * non-empty request was served, every served kind is selected.
   */
  initialKinds: DiaryKind[] | null;
  events: DiaryEventResponse[];
  typesServed: DiaryKind[];
  loadError: boolean;
  members: DiaryMemberOption[];
  matters: DiaryMatterOption[];
  currentMemberId: string | null;
  isAdminOrOwner: boolean;
  /** MANAGE_LEGAL (or admin / owner): gates every court-date / prescription write affordance. */
  canManageLegal: boolean;
  /** Validated `?feeEarner=` (`mine` | `all` | member id); `null` = the role default. */
  initialFeeEarner?: string | null;
  /** Validated `?matter=`; `null` = every matter. */
  initialMatterId?: string | null;
}

interface UrlState {
  from: string;
  view: DiaryView;
  kinds: DiaryKind[];
  feeEarner: string;
  matterId: string | null;
}

function sameKinds(a: DiaryKind[], b: DiaryKind[]): boolean {
  return a.length === b.length && a.every((k) => b.includes(k));
}

/** Navigation through the date input waits for a plausible, complete date (partial typing). */
function isNavigableDate(value: string): boolean {
  return isValidIsoDate(value) && Number(value.slice(0, 4)) >= 1900;
}

/**
 * Diary toolbar (view tabs, range navigation, create buttons), filters, the active view, and the
 * row-action dialogs. Range changes and view changes that need another window navigate
 * (router.push — the server loads one window per visit); view switches inside the same window
 * (Month ↔ List) and filter changes are client-only, kept shareable with history.replaceState.
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
  canManageLegal,
  initialFeeEarner = null,
  initialMatterId = null,
}: DiaryPageClientProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [, startTransition] = useTransition();

  const allServed = typesServed;
  const showMine = currentMemberId !== null;
  const defaultFeeEarner = !isAdminOrOwner && currentMemberId ? FEE_EARNER_MINE : FEE_EARNER_ALL;
  const current: DiaryWindow = { from, to };

  // Props seed state on mount only (the loader keys this component on the window), so a refresh
  // that re-reads the URL keeps the live selection.
  const [view, setView] = useState<DiaryView>(initialView);
  const [kinds, setKinds] = useState<DiaryKind[]>(() => {
    if (initialKinds === null) return allServed;
    const preselected = initialKinds.filter((k) => typesServed.includes(k));
    return preselected.length > 0 || initialKinds.length === 0 ? preselected : allServed;
  });
  const [feeEarner, setFeeEarner] = useState<string>(initialFeeEarner ?? defaultFeeEarner);
  const [matterId, setMatterId] = useState<string | null>(initialMatterId);
  const [target, setTarget] = useState<{
    event: DiaryEventResponse;
    action: Exclude<DiaryRowAction, "mark-done">;
  } | null>(null);

  /** URL order: from, view (≠ week), types (≠ every served kind), feeEarner (≠ default), matter. */
  function buildUrl(next: Partial<UrlState> = {}): string {
    const s: UrlState = { from, view, kinds, feeEarner, matterId, ...next };
    const qs = new URLSearchParams();
    qs.set("from", s.from);
    if (s.view !== "week") qs.set("view", s.view);
    if (!sameKinds(s.kinds, allServed)) qs.set("types", s.kinds.join(","));
    if (s.feeEarner !== defaultFeeEarner) qs.set("feeEarner", s.feeEarner);
    if (s.matterId) qs.set("matter", s.matterId);
    return `${pathname}?${qs.toString()}`;
  }

  function syncUrl(next: Partial<UrlState>) {
    window.history.replaceState(null, "", buildUrl(next));
  }

  function navigate(next: Partial<UrlState>) {
    router.push(buildUrl(next), { scroll: false });
  }

  function goToWindow(nextWindow: DiaryWindow) {
    if (!sameWindow(nextWindow, current)) navigate({ from: nextWindow.from });
  }

  function handleViewChange(value: string) {
    const next = parseDiaryView(value);
    const anchor = windowContains(current, today) ? today : from;
    const nextWindow = diaryWindow(next, anchor);
    if (sameWindow(nextWindow, current)) {
      setView(next);
      syncUrl({ view: next });
    } else {
      navigate({ from: nextWindow.from, view: next });
    }
  }

  function handleDateInput(value: string) {
    if (isNavigableDate(value)) goToWindow(diaryWindow(view, value));
  }

  function openDay(iso: string) {
    navigate({ from: iso, view: "day" });
  }

  function handleKindsChange(next: DiaryKind[]) {
    setKinds(next);
    syncUrl({ kinds: next });
  }

  function handleFeeEarnerChange(next: string) {
    setFeeEarner(next);
    syncUrl({ feeEarner: next });
  }

  function handleMatterChange(next: string | null) {
    setMatterId(next);
    syncUrl({ matterId: next });
  }

  function clearFilters() {
    setKinds(allServed);
    setFeeEarner(FEE_EARNER_ALL);
    setMatterId(null);
    syncUrl({ kinds: allServed, feeEarner: FEE_EARNER_ALL, matterId: null });
  }

  function handleAction(event: DiaryEventResponse, action: DiaryRowAction) {
    if (action !== "mark-done") {
      setTarget({ event, action });
      return;
    }
    startTransition(async () => {
      try {
        const result = await completeTask(slug, event.id, event.matterId);
        if (result.success) {
          toast.success("Task marked done");
          router.refresh();
        } else {
          toast.error(result.error ?? "Failed to mark the task done.");
        }
      } catch {
        // Server-action errors arrive serialized: never rely on instanceof checks here.
        toast.error("Failed to mark the task done.");
      }
    });
  }

  function closeDialog(open: boolean) {
    if (!open) setTarget(null);
  }

  function afterWrite() {
    setTarget(null);
    router.refresh();
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
  // Every window is ≤ 31 days, so the fee earner is filtered client-side
  // (shouldUseServerFeeEarnerFilter is false).
  const filtered = filterDiaryEvents(events, { kinds, feeEarnerId, matterId });
  const { band, inWindow } = partitionOverdueBand(filtered, from);
  const unit = DIARY_VIEW_UNIT[view];
  const canCreateCourtDate = typesServed.includes("COURT_DATE") && canManageLegal;
  const canCreatePrescription = typesServed.includes("PRESCRIPTION") && canManageLegal;
  const containsToday = windowContains(current, today);

  let body: React.ReactNode;
  if (events.length === 0) {
    body = (
      <EmptyState
        icon={CalendarDays}
        title={`Nothing in the diary for this ${unit}.`}
        description={
          typesServed.includes("COURT_DATE")
            ? "Court dates, prescription dates, matter due dates and task due dates appear here."
            : "Matter due dates and task due dates appear here."
        }
        action={
          canCreateCourtDate ? (
            <CreateCourtDateDialog
              slug={slug}
              triggerLabel="Add a court date"
              triggerVariant="outline"
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
        description={`Change the filters or clear them to see the whole ${unit}.`}
        actionLabel="Clear filters"
        onAction={clearFilters}
      />
    );
  } else if (view === "day") {
    body = <DiaryDayView events={inWindow} band={band} slug={slug} />;
  } else if (view === "month") {
    body = (
      <DiaryMonthView
        events={inWindow}
        band={band}
        from={from}
        today={today}
        slug={slug}
        onSelectDay={openDay}
      />
    );
  } else if (view === "list") {
    body = <DiaryListView events={inWindow} band={band} slug={slug} />;
  } else {
    body = (
      <DiaryWeekView
        events={inWindow}
        band={band}
        from={from}
        today={today}
        slug={slug}
        onSelectDay={openDay}
      />
    );
  }

  return (
    <Tabs value={view} onValueChange={handleViewChange} className="gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <TabsList aria-label="Diary view">
          {DIARY_VIEWS.map((v) => (
            <TabsTrigger key={v} value={v}>
              {DIARY_VIEW_LABELS[v]}
            </TabsTrigger>
          ))}
        </TabsList>

        <div role="group" aria-label="Date range" className="flex items-center gap-1.5">
          <Button
            type="button"
            variant="outline"
            size="icon-sm"
            aria-label={`Previous ${unit}`}
            onClick={() => goToWindow(stepDiaryWindow(view, from, -1))}
          >
            <ChevronLeft aria-hidden="true" className="size-4" />
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={containsToday}
            onClick={() => goToWindow(diaryWindow(view, today))}
          >
            Today
          </Button>
          <Button
            type="button"
            variant="outline"
            size="icon-sm"
            aria-label={`Next ${unit}`}
            onClick={() => goToWindow(stepDiaryWindow(view, from, 1))}
          >
            <ChevronRight aria-hidden="true" className="size-4" />
          </Button>
          {/* Uncontrolled: a controlled value would reset the field mid-way through typing a
              year. The component remounts per window, so defaultValue stays in step. */}
          <Input
            type="date"
            aria-label="Go to date"
            defaultValue={from}
            onChange={(e) => handleDateInput(e.target.value)}
            className="h-8 w-40"
          />
        </div>

        <p className="text-sm text-slate-600 dark:text-slate-400" data-testid="diary-range">
          {formatDiaryRangeLabel(view, current)}
        </p>

        {(canCreateCourtDate || canCreatePrescription) && (
          <div className="ml-auto flex flex-wrap items-center gap-2">
            {canCreatePrescription && (
              <CreatePrescriptionDialog
                slug={slug}
                triggerLabel="New prescription tracker"
                triggerVariant="outline"
                onSuccess={() => router.refresh()}
              />
            )}
            {canCreateCourtDate && (
              <CreateCourtDateDialog
                slug={slug}
                triggerLabel="New court date"
                onSuccess={() => router.refresh()}
              />
            )}
          </div>
        )}
      </div>

      <DiaryFilterRow
        typesServed={typesServed}
        kinds={kinds}
        onKindsChange={handleKindsChange}
        feeEarner={feeEarner}
        onFeeEarnerChange={handleFeeEarnerChange}
        members={members}
        showMine={showMine}
        matterId={matterId}
        onMatterChange={handleMatterChange}
        matters={matters}
      />

      <TabsContent value={view}>
        <DiaryActionsProvider
          value={{ canManageLegal, isAdminOrOwner, currentMemberId, onAction: handleAction }}
        >
          {body}
        </DiaryActionsProvider>
      </TabsContent>

      {target?.action === "postpone" && (
        <PostponeDialog
          slug={slug}
          courtDateId={target.event.id}
          open
          onOpenChange={closeDialog}
          onSuccess={afterWrite}
        />
      )}
      {target?.action === "outcome" && (
        <OutcomeDialog
          slug={slug}
          courtDateId={target.event.id}
          open
          onOpenChange={closeDialog}
          onSuccess={afterWrite}
        />
      )}
      {target?.action === "cancel" && (
        <CancelCourtDateDialog
          slug={slug}
          courtDateId={target.event.id}
          open
          onOpenChange={closeDialog}
          onSuccess={afterWrite}
        />
      )}
    </Tabs>
  );
}
