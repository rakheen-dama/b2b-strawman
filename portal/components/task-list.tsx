import { ClipboardList } from "lucide-react";
import { StatusBadge } from "@/components/status-badge";
import type { PortalTask } from "@/lib/types";

interface TaskListProps {
  tasks: PortalTask[];
}

export function TaskList({ tasks }: TaskListProps) {
  if (tasks.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <ClipboardList className="mb-4 size-10 text-slate-300" />
        <p className="text-sm text-slate-500">No tasks yet.</p>
      </div>
    );
  }

  return (
    <>
      {/* Mobile: Card layout */}
      <div
        data-testid="task-list-mobile"
        className="flex flex-col gap-3 md:hidden"
      >
        {tasks.map((task) => (
          <div
            key={task.id}
            className="flex flex-col gap-2 rounded-lg border border-slate-200/80 bg-white p-4"
          >
            <div className="flex items-start justify-between gap-3">
              <p className="min-w-0 flex-1 text-sm font-medium text-slate-900">
                {task.name}
              </p>
              <StatusBadge status={task.status} variant="task" />
            </div>
            <p className="text-xs text-slate-500">
              {task.assigneeName ?? "Unassigned"}
            </p>
          </div>
        ))}
      </div>

      {/* Desktop: Table layout */}
      <div
        data-testid="task-list-desktop"
        className="hidden overflow-hidden rounded-lg border border-slate-200/80 md:block"
      >
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
            <tr>
              <th className="px-4 py-3">Task</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Assignee</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {tasks.map((task) => (
              <tr key={task.id} className="bg-white">
                <td className="px-4 py-3 font-medium text-slate-900">
                  {task.name}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge status={task.status} variant="task" />
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {task.assigneeName ?? "Unassigned"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
