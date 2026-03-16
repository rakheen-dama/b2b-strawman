import { api } from "@/lib/api";
import type { MyWorkTasksResponse, MyWorkTimeEntryItem } from "@/lib/types";
import { WeeklyTimeGrid } from "@/components/time-tracking/weekly-time-grid";
import type { GridTaskRow } from "@/components/time-tracking/weekly-time-grid";

function getCurrentWeekMonday(): string {
  const now = new Date();
  const day = now.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  const monday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate() + diff,
  );
  return monday.toLocaleDateString("en-CA");
}

function addDaysToIso(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  date.setDate(date.getDate() + days);
  return date.toLocaleDateString("en-CA");
}

export default async function TimesheetPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ week?: string }>;
}) {
  const { slug } = await params;
  const { week } = await searchParams;

  const isValidWeekParam = week != null && /^\d{4}-\d{2}-\d{2}$/.test(week);
  const weekStart = isValidWeekParam ? week : getCurrentWeekMonday();
  const weekEnd = addDaysToIso(weekStart, 6);

  let tasksData: MyWorkTasksResponse = { assigned: [], unassigned: [] };
  try {
    tasksData = await api.get<MyWorkTasksResponse>("/api/my-work/tasks");
  } catch (error) {
    console.error("Failed to fetch tasks for timesheet:", error);
  }

  let existingEntries: MyWorkTimeEntryItem[] = [];
  try {
    existingEntries = await api.get<MyWorkTimeEntryItem[]>(
      `/api/my-work/time-entries?from=${weekStart}&to=${weekEnd}`,
    );
  } catch (error) {
    console.error("Failed to fetch time entries for timesheet:", error);
  }

  // Build GridTaskRow[] from assigned tasks
  const assignedTasks: GridTaskRow[] = tasksData.assigned.map((t) => ({
    id: t.id,
    projectId: t.projectId,
    projectName: t.projectName,
    title: t.title,
  }));

  // De-duplicate: tasks with existing entries first, then remaining assigned tasks
  const entryTaskIds = new Set(existingEntries.map((e) => e.taskId));
  const prePopulatedRows = [
    ...assignedTasks.filter((t) => entryTaskIds.has(t.id)),
    ...assignedTasks.filter((t) => !entryTaskIds.has(t.id)).slice(0, 5),
  ];
  // De-duplicate by id
  const seen = new Set<string>();
  const uniqueRows = prePopulatedRows.filter((t) => {
    if (seen.has(t.id)) return false;
    seen.add(t.id);
    return true;
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-slate-900 dark:text-slate-100">
          Timesheet
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Log your weekly hours across all tasks.
        </p>
      </div>
      <WeeklyTimeGrid
        tasks={uniqueRows}
        existingEntries={existingEntries}
        weekStart={weekStart}
        allTasks={assignedTasks}
        slug={slug}
      />
    </div>
  );
}
