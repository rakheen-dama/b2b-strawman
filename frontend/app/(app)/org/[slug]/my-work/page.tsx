import { auth, currentUser } from "@clerk/nextjs/server";
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
} from "@/lib/types";
import { WeeklyTimeSummary } from "@/components/my-work/weekly-time-summary";
import { TodayTimeEntries } from "@/components/my-work/today-time-entries";
import { PersonalKpis } from "@/components/my-work/personal-kpis";
import { TimeBreakdown } from "@/components/my-work/time-breakdown";
import { UpcomingDeadlines } from "@/components/my-work/upcoming-deadlines";
import { MyWorkHeader } from "./my-work-header";
import { MyWorkTasksClient } from "./my-work-tasks-client";
import { createMyWorkViewAction } from "./view-actions";
import { fetchPersonalDashboard } from "@/lib/actions/dashboard";
import { ApiError } from "@/lib/api";

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
    now.getDate() + diffToMonday
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
  const { orgRole } = await auth();
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
      `/api/my-work/time-summary?from=${from}&to=${to}`
    );
  } catch {
    // Non-fatal: show empty time summary
  }

  const today = new Date().toLocaleDateString("en-CA");

  let todayEntries: MyWorkTimeEntryItem[] = [];
  try {
    todayEntries = await api.get<MyWorkTimeEntryItem[]>(
      `/api/my-work/time-entries?from=${today}&to=${today}`
    );
  } catch {
    // Non-fatal: show empty today entries
  }

  // Resolve current member ID and org members in a single fetch
  let currentMemberId = "";
  let members: { id: string; name: string; email: string }[] = [];
  try {
    const [user, orgMembers] = await Promise.all([
      currentUser(),
      api.get<OrgMember[]>("/api/members"),
    ]);
    const email = user?.primaryEmailAddress?.emailAddress;
    if (email) {
      const match = orgMembers.find((m) => m.email === email);
      if (match) currentMemberId = match.id;
    }
    members = orgMembers.map((m) => ({ id: m.id, name: m.name, email: m.email }));
  } catch {
    // Non-fatal
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

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";
  const canManage = isAdmin;

  // Inline server action for creating task views
  async function handleCreateTaskView(
    req: import("@/lib/types").CreateSavedViewRequest,
  ) {
    "use server";
    return createMyWorkViewAction(slug, req);
  }

  // Determine period label from date range for KPIs
  const periodLabel = resolvedSearchParams.from ? undefined : "This Week";

  return (
    <div className="space-y-8">
      {/* Page Header with Date Range Selector */}
      <MyWorkHeader from={from} to={to} />

      {/* Personal KPI Cards */}
      <PersonalKpis data={personalDashboard} periodLabel={periodLabel} />

      {/* Dashboard Widgets: Time Breakdown + Upcoming Deadlines */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <TimeBreakdown
          data={personalDashboard?.projectBreakdown ?? null}
        />
        <UpcomingDeadlines
          deadlines={personalDashboard?.upcomingDeadlines ?? null}
        />
      </div>

      {/* Two-column layout: tasks left, time summary right */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
        {/* Tasks Column (wider) */}
        <div className="space-y-8 lg:col-span-2">
          <MyWorkTasksClient
            assigned={tasksData.assigned}
            unassigned={tasksData.unassigned}
            slug={slug}
            orgRole={orgRole ?? ""}
            canManage={canManage}
            currentMemberId={currentMemberId}
            members={members}
            allTags={allTags}
            fieldDefinitions={fieldDefinitions}
            fieldGroups={fieldGroups}
            groupMembers={groupMembers}
            savedViews={savedViews}
            onSave={handleCreateTaskView}
          />
        </div>

        {/* Time Summary Column */}
        <div className="space-y-6">
          <WeeklyTimeSummary
            initialSummary={timeSummary}
            initialFrom={from}
          />
          <TodayTimeEntries entries={todayEntries} />
        </div>
      </div>
    </div>
  );
}
