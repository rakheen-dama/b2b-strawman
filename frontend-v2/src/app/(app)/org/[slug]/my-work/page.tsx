import { api } from "@/lib/api";
import { ApiError, handleApiError } from "@/lib/api";
import { fetchPersonalDashboard } from "@/lib/actions/dashboard";
import type {
  MyWorkTasksResponse,
  MyWorkTimeSummary,
  MyWorkTimeEntryItem,
} from "@/lib/types";
import { PageHeader } from "@/components/layout/page-header";
import { PersonalKpis } from "@/components/my-work/personal-kpis";
import { TaskGroup } from "@/components/my-work/task-group";
import { TodayTimeEntries } from "@/components/my-work/today-time-entries";
import { WeeklyTimeSummary } from "@/components/my-work/weekly-time-summary";
import { TimeEntryFab } from "@/components/shell/time-entry-fab";

/**
 * Resolves date range from search params, defaulting to current week
 * (Monday to Sunday).
 */
function resolveMyWorkDateRange(searchParams: {
  from?: string;
  to?: string;
}): { from: string; to: string } {
  if (searchParams.from && searchParams.to) {
    return { from: searchParams.from, to: searchParams.to };
  }

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

  // Fetch time summary for the current week
  let timeSummary: MyWorkTimeSummary | null = null;
  try {
    timeSummary = await api.get<MyWorkTimeSummary>(
      `/api/my-work/time-summary?from=${from}&to=${to}`,
    );
  } catch {
    // Non-fatal
  }

  // Fetch today's time entries
  const today = new Date().toLocaleDateString("en-CA");
  let todayEntries: MyWorkTimeEntryItem[] = [];
  try {
    todayEntries = await api.get<MyWorkTimeEntryItem[]>(
      `/api/my-work/time-entries?from=${today}&to=${today}`,
    );
  } catch {
    // Non-fatal
  }

  const periodLabel = resolvedSearchParams.from ? undefined : "This Week";

  return (
    <div className="space-y-6">
      <PageHeader
        title="My Work"
        description="Your tasks, time entries, and personal metrics"
      />

      {/* Personal KPIs */}
      <PersonalKpis data={personalDashboard} periodLabel={periodLabel} />

      {/* Two-column layout: tasks left, time summary right */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Tasks Column (wider) */}
        <div className="space-y-6 lg:col-span-2">
          <TaskGroup
            title="Assigned to Me"
            tasks={tasksData.assigned}
            orgSlug={slug}
            emptyMessage="No tasks assigned to you"
          />
          {tasksData.unassigned.length > 0 && (
            <TaskGroup
              title="Unassigned"
              tasks={tasksData.unassigned}
              orgSlug={slug}
              emptyMessage="No unassigned tasks"
            />
          )}
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

      {/* Floating Log Time button */}
      <TimeEntryFab />
    </div>
  );
}
