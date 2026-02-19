import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { DataRequestResponse } from "@/lib/types";
import { formatLocalDate } from "@/lib/format";

interface DataRequestsSectionProps {
  openCount: number;
  urgentRequests: DataRequestResponse[];
  orgSlug: string;
}

function getDaysUntilDeadline(deadline: string): number {
  const [year, month, day] = deadline.split("-").map(Number);
  const deadlineDate = new Date(year, month - 1, day);
  return Math.floor((deadlineDate.getTime() - Date.now()) / 86400000);
}

function getDeadlineColor(daysUntil: number): string {
  if (daysUntil < 0) return "text-red-600 dark:text-red-400";
  if (daysUntil < 3) return "text-red-600 dark:text-red-400";
  if (daysUntil <= 7) return "text-amber-600 dark:text-amber-400";
  return "text-emerald-600 dark:text-emerald-400";
}

function formatDeadlineCountdown(daysUntil: number): string {
  if (daysUntil < 0) return `${Math.abs(daysUntil)}d overdue`;
  if (daysUntil === 0) return "Due today";
  return `${daysUntil}d remaining`;
}

export function DataRequestsSection({
  openCount,
  urgentRequests,
  orgSlug,
}: DataRequestsSectionProps) {
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Data Requests</h2>
        {openCount > 0 && (
          <Badge variant="neutral">
            {openCount} open {openCount === 1 ? "request" : "requests"}
          </Badge>
        )}
      </div>
      {urgentRequests.length === 0 ? (
        <p className="text-sm text-slate-500 dark:text-slate-400">No open data requests</p>
      ) : (
        <div className="space-y-3">
          <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
                  <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                    Customer
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                    Type
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                    Deadline
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody>
                {urgentRequests.map((req) => {
                  const daysUntil = getDaysUntilDeadline(req.deadline);
                  return (
                    <tr
                      key={req.id}
                      className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                    >
                      <td className="px-4 py-3 font-medium text-slate-950 dark:text-slate-50">
                        {req.customerName}
                      </td>
                      <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                        {req.requestType}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex flex-col">
                          <span className="text-slate-600 dark:text-slate-400">
                            {formatLocalDate(req.deadline)}
                          </span>
                          <span className={cn("text-xs font-medium", getDeadlineColor(daysUntil))}>
                            {formatDeadlineCountdown(daysUntil)}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant="neutral">{req.status}</Badge>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Link
            href={`/org/${orgSlug}/compliance/requests`}
            className="inline-block text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400"
          >
            View All Requests
          </Link>
        </div>
      )}
    </div>
  );
}
