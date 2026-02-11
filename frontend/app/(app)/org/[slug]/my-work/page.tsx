import { api, handleApiError } from "@/lib/api";
import type { MyWorkTasksResponse, MyWorkTimeSummary } from "@/lib/types";
import { AssignedTaskList } from "@/components/my-work/assigned-task-list";
import { ApiError } from "@/lib/api";
import { formatDuration } from "@/lib/format";

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
          <AssignedTaskList
            tasks={tasksData.assigned}
            slug={slug}
          />
        </div>

        {/* Time Summary Column */}
        <div className="space-y-6">
          <div className="rounded-lg border border-olive-200 bg-white p-6 dark:border-olive-800 dark:bg-olive-950">
            <h2 className="font-semibold text-olive-900 dark:text-olive-100">
              This Week
            </h2>
            {timeSummary && timeSummary.totalMinutes > 0 ? (
              <div className="mt-4 space-y-4">
                {/* Summary Stats */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-olive-500 dark:text-olive-400">
                      Billable
                    </p>
                    <p className="font-display text-2xl text-emerald-600 dark:text-emerald-400">
                      {formatDuration(timeSummary.billableMinutes)}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-olive-500 dark:text-olive-400">
                      Non-billable
                    </p>
                    <p className="font-display text-2xl text-olive-600 dark:text-olive-400">
                      {formatDuration(timeSummary.nonBillableMinutes)}
                    </p>
                  </div>
                </div>

                <div className="border-t border-olive-200 pt-4 dark:border-olive-800">
                  <p className="text-sm text-olive-500 dark:text-olive-400">
                    Total
                  </p>
                  <p className="font-display text-2xl text-olive-900 dark:text-olive-100">
                    {formatDuration(timeSummary.totalMinutes)}
                  </p>
                </div>

                {/* By Project Breakdown */}
                {timeSummary.byProject.length > 0 && (
                  <div className="border-t border-olive-200 pt-4 dark:border-olive-800">
                    <h3 className="mb-3 text-sm font-medium text-olive-700 dark:text-olive-300">
                      By Project
                    </h3>
                    <div className="space-y-2">
                      {timeSummary.byProject.map((project) => (
                        <div
                          key={project.projectId}
                          className="flex items-center justify-between text-sm"
                        >
                          <span className="truncate text-olive-700 dark:text-olive-300">
                            {project.projectName}
                          </span>
                          <span className="shrink-0 font-medium text-olive-900 dark:text-olive-100">
                            {formatDuration(project.totalMinutes)}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <p className="mt-4 text-sm text-olive-500 dark:text-olive-400">
                No time tracked this week
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
