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
    <div className="overflow-hidden rounded-lg border border-slate-200/80">
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
  );
}
