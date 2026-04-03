import { getCurrentUserEmail } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  api,
  handleApiError,
  getViews,
  getFieldDefinitions,
  getFieldGroups,
  getGroupMembers,
  getTags,
} from "@/lib/api";
import type {
  MyWorkTasksResponse,
  MyWorkTimeSummary,
  MyWorkTimeEntryItem,
  OrgMember,
  SavedViewResponse,
  TagResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
  ExpenseResponse,
} from "@/lib/types";
import { WeeklyTimeSummary } from "@/components/my-work/weekly-time-summary";
import { TodayTimeEntries } from "@/components/my-work/today-time-entries";
import { MyExpenses } from "@/components/my-work/my-expenses";
import { TimeBreakdown } from "@/components/my-work/time-breakdown";
import { TodaysAgenda } from "@/components/dashboard/todays-agenda";
import { WeeklyRhythmStripClient } from "./weekly-rhythm-strip-client";
import { MyWorkHeader } from "./my-work-header";
import { MyWorkTasksClient } from "./my-work-tasks-client";
import { createMyWorkViewAction } from "./view-actions";
import { fetchPersonalDashboard } from "@/lib/actions/dashboard";
import { getMyExpenses } from "@/lib/actions/expense-actions";
import { ApiError } from "@/lib/api";
import { EmptyState } from "@/components/empty-state";
import { createMessages } from "@/lib/messages";
import { ClipboardList } from "lucide-react";
import Link from "next/link";

/**
 * Resolves date range from search params, defaulting to current week
 * (Monday to Sunday) instead of current month.
 */
function resolveMyWorkDateRange(searchParams: {
  from?: string;
  to?: string;
}): { from: string; to: string } {
  if (searchParams.from && searchParams.to) {
    return { from: searchParams.from, to: searchParams.to };
  }

  // Default to current week (Monday - Sunday)
  const now = new Date();
  const day = now.getDay(); // 0=Sun, 1=Mon, ..., 6=Sat
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate() + diffToMonday,
  );
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);

  const formatDate = (d: Date): string => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${dd}`;
  };

  return {
    from: formatDate(monday),
    to: formatDate(sunday),
  };
}

export default async function MyWorkPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ from?: string; to?: string }>;
}) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();
  const resolvedSearchParams = await searchParams;
  const { from, to } = resolveMyWorkDateRange(resolvedSearchParams);

  // Fetch tasks (not affected by date range)
  let tasksData: MyWorkTasksResponse = { assigned: [], unassigned: [] };
  try {
    tasksData = await api.get<MyWorkTasksResponse>("/api/my-work/tasks");
  } catch (error) {
    if (
      error instanceof ApiError &&
      (error.status === 401 || error.status === 404)
    ) {
      handleApiError(error);
    }
    // Non-fatal for other errors: show empty state
  }

  // Fetch personal dashboard data (affected by date range)
  const personalDashboard = await fetchPersonalDashboard(from, to);

  // Fetch time summary for the current week (uses same date range as dashboard)
  let timeSummary: MyWorkTimeSummary | null = null;
  try {
    timeSummary = await api.get<MyWorkTimeSummary>(
      `/api/my-work/time-summary?from=${from}&to=${to}`,
    );
  } catch {
    // Non-fatal: show empty time summary
  }

  const today = new Date().toLocaleDateString("en-CA");

  let todayEntries: MyWorkTimeEntryItem[] = [];
  try {
    todayEntries = await api.get<MyWorkTimeEntryItem[]>(
      `/api/my-work/time-entries?from=${today}&to=${today}`,
    );
  } catch {
    // Non-fatal: show empty today entries
  }

  // Fetch week entries for the rhythm strip
  let weekEntries: MyWorkTimeEntryItem[] = [];
  try {
    weekEntries = await api.get<MyWorkTimeEntryItem[]>(
      `/api/my-work/time-entries?from=${from}&to=${to}`,
    );
  } catch {
    // Non-fatal
  }

  // Fetch user's recent expenses
  let myExpenses: ExpenseResponse[] = [];
  try {
    const expenseResult = await getMyExpenses({ size: 10 });
    myExpenses = expenseResult.content;
  } catch {
    // Non-fatal
  }

  // Resolve current member ID and org members in a single fetch
  let currentMemberId = "";
  let members: { id: string; name: string; email: string }[] = [];
  try {
    const [email, orgMembers] = await Promise.all([
      getCurrentUserEmail(),
      api.get<OrgMember[]>("/api/members"),
    ]);
    if (email) {
      const match = orgMembers.find((m) => m.email === email);
      if (match) currentMemberId = match.id;
    }
    members = orgMembers.map((m) => ({
      id: m.id,
      name: m.name,
      email: m.email,
    }));
  } catch (e) {
    console.error("Failed to resolve current member:", e);
  }

  // Saved views for TASK
  let savedViews: SavedViewResponse[] = [];
  try {
    savedViews = await getViews("TASK");
  } catch {
    // Non-fatal
  }

  // Tags and field definitions for TASK
  let allTags: TagResponse[] = [];
  let fieldDefinitions: FieldDefinitionResponse[] = [];
  let fieldGroups: FieldGroupResponse[] = [];
  const groupMembers: Record<string, FieldGroupMemberResponse[]> = {};
  try {
    const [tags, defs, groups] = await Promise.all([
      getTags(),
      getFieldDefinitions("TASK"),
      getFieldGroups("TASK"),
    ]);
    allTags = tags;
    fieldDefinitions = defs;
    fieldGroups = groups;

    const activeGroupIds = groups.filter((g) => g.active).map((g) => g.id);
    if (activeGroupIds.length > 0) {
      const memberResults = await Promise.allSettled(
        activeGroupIds.map((gId) => getGroupMembers(gId)),
      );
      memberResults.forEach((result, i) => {
        if (result.status === "fulfilled") {
          groupMembers[activeGroupIds[i]] = result.value;
        }
      });
    }
  } catch {
    // Non-fatal
  }

  const isAdmin = caps.isAdmin || caps.isOwner;
  const canManage = isAdmin;
  const canCreateShared = isAdmin;

  // Inline server action for creating task views
  async function handleCreateTaskView(
    req: import("@/lib/types").CreateSavedViewRequest,
  ) {
    "use server";
    return createMyWorkViewAction(slug, req);
  }

  const { t } = createMessages("empty-states");
  const hasNoTasks =
    tasksData.assigned.length === 0 && tasksData.unassigned.length === 0;

  return (
    <div className="space-y-6">
      {/* Page Header with Date Range Selector */}
      <div className="flex items-center justify-between">
        <MyWorkHeader from={from} to={to} />
        <Link
          href={`/org/${slug}/my-work/timesheet`}
          className="rounded-full border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
        >
          Timesheet
        </Link>
      </div>

      {/* Hero: Today's Agenda */}
      <TodaysAgenda
        tasks={tasksData.assigned}
        todayEntries={todayEntries}
        upcomingDeadlines={personalDashboard?.upcomingDeadlines ?? []}
        weeklyCapacityHours={40}
      />

      {/* Weekly Rhythm Strip */}
      <WeeklyRhythmStripClient weekEntries={weekEntries} from={from} />

      {/* Two-column work panels */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-5">
        {/* Left: Tasks (col-span-3) */}
        <div className="lg:col-span-3">
          {hasNoTasks ? (
            <EmptyState
              icon={ClipboardList}
              title={t("myWork.list.heading")}
              description={t("myWork.list.description")}
              actionLabel={t("myWork.list.cta")}
              actionHref={`/org/${slug}/projects`}
            />
          ) : (
            <MyWorkTasksClient
              assigned={tasksData.assigned}
              unassigned={tasksData.unassigned}
              slug={slug}
              isAdmin={isAdmin}
              canManage={canManage}
              canCreateShared={canCreateShared}
              currentMemberId={currentMemberId}
              members={members}
              allTags={allTags}
              fieldDefinitions={fieldDefinitions}
              fieldGroups={fieldGroups}
              groupMembers={groupMembers}
              savedViews={savedViews}
              onSave={handleCreateTaskView}
            />
          )}
        </div>

        {/* Right: Time/Activity (col-span-2) */}
        <div className="space-y-4 lg:col-span-2">
          <TimeBreakdown
            data={personalDashboard?.projectBreakdown ?? null}
          />
          <TodayTimeEntries entries={todayEntries} />
        </div>
      </div>

      {/* Extended widgets */}
      <div
        data-testid="extended-widgets"
        className="grid grid-cols-1 gap-4 md:grid-cols-2"
      >
        <WeeklyTimeSummary
          initialSummary={timeSummary}
          initialFrom={from}
        />
        <MyExpenses expenses={myExpenses} />
      </div>
    </div>
  );
}
