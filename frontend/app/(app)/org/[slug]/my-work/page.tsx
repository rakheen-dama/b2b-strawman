import { api, handleApiError } from "@/lib/api";
import type {
  MyWorkTasksResponse,
  MyWorkTimeSummary,
  MyWorkTimeEntryItem,
} from "@/lib/types";
import { AssignedTaskList } from "@/components/my-work/assigned-task-list";
import { AvailableTaskList } from "@/components/my-work/available-task-list";
import { WeeklyTimeSummary } from "@/components/my-work/weekly-time-summary";
import { TodayTimeEntries } from "@/components/my-work/today-time-entries";
import { ApiError } from "@/lib/api";

/** Returns Monday (start) and Sunday (end) of the current week as 'YYYY-MM-DD'. */
function getCurrentWeekRange(): { from: string; to: string } {
  const now = new Date();
  const day = now.getDay(); // 0=Sun, 1=Mon, ..., 6=Sat
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(now);
  monday.setDate(now.getDate() + diffToMonday);

  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);

  return {
    from: monday.toLocaleDateString("en-CA"),
    to: sunday.toLocaleDateString("en-CA"),
  };
}

export default async function MyWorkPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let tasksData: MyWorkTasksResponse = { assigned: [], unassigned: [] };
  try {
    tasksData = await api.get<MyWorkTasksResponse>("/api/my-work/tasks");
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 404)) {
      handleApiError(error);
    }
    // Non-fatal for other errors: show empty state
  }

  const { from, to } = getCurrentWeekRange();

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

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div>
        <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
          My Work
        </h1>
        <p className="mt-1 text-sm text-olive-600 dark:text-olive-400">
          Your tasks and time tracking across all projects
        </p>
      </div>

      {/* Two-column layout: tasks left, time summary right */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
        {/* Tasks Column (wider) */}
        <div className="space-y-8 lg:col-span-2">
          <AssignedTaskList tasks={tasksData.assigned} slug={slug} />
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
