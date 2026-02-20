import Link from "next/link";
import { formatDate } from "@/lib/format";
import type { ScheduleExecutionResponse } from "@/lib/api/schedules";

interface ExecutionHistoryProps {
  executions: ScheduleExecutionResponse[];
  slug: string;
}

export function ExecutionHistory({ executions, slug }: ExecutionHistoryProps) {
  if (executions.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No executions yet — projects will appear here after the first automated run.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Period
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Project
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Executed
            </th>
          </tr>
        </thead>
        <tbody>
          {executions.map((execution) => (
            <tr
              key={execution.id}
              className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
            >
              <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                {formatDate(execution.periodStart)} – {formatDate(execution.periodEnd)}
              </td>
              <td className="px-4 py-3">
                <Link
                  href={`/org/${slug}/projects/${execution.projectId}`}
                  className="text-sm font-medium text-slate-900 hover:text-teal-600 dark:text-slate-100 dark:hover:text-teal-400"
                >
                  {execution.projectName}
                </Link>
              </td>
              <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                {formatDate(execution.executedAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
