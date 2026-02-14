import { api, handleApiError } from "@/lib/api";
import type {
  MyWorkTasksResponse,
  MyWorkTimeSummary,
  MyWorkTimeEntryItem,
} from "@/lib/types";
import { UrgencyTaskList } from "@/components/my-work/urgency-task-list";
import { AvailableTaskList } from "@/components/my-work/available-task-list";
import { WeeklyTimeSummary } from "@/components/my-work/weekly-time-summary";
import { TodayTimeEntries } from "@/components/my-work/today-time-entries";
import { PersonalKpis } from "@/components/my-work/personal-kpis";
import { TimeBreakdown } from "@/components/my-work/time-breakdown";
import { UpcomingDeadlines } from "@/components/my-work/upcoming-deadlines";
import { MyWorkHeader } from "./my-work-header";
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
          <UrgencyTaskList tasks={tasksData.assigned} slug={slug} />
          <AvailableTaskList tasks={tasksData.unassigned} slug={slug} />
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
