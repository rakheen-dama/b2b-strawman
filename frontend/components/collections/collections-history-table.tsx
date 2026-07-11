import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { formatDateTime } from "@/lib/format";
import type { CollectionActivityResponse } from "@/lib/api/collections";

interface CollectionsHistoryTableProps {
  activities: CollectionActivityResponse[];
  slug: string;
  /** Copy shown when there are no activity rows. */
  emptyMessage?: string;
}

/**
 * Presentational, read-only chase-history ledger shared by the invoice-detail
 * (591C.1) and customer-detail (591C.2) surfaces. Renders one row per
 * collection activity — stage, status (+ humanised reason), days-overdue at the
 * time of action, proposal/update timestamps, and a link to the AI reviews
 * queue when the row is a PROPOSED reminder still awaiting approval.
 *
 * Server-compatible: no hooks, handlers, or browser APIs — must NOT carry
 * "use client". Data is fetched in the consuming RSC pages and passed as props.
 */
export function CollectionsHistoryTable({
  activities,
  slug,
  emptyMessage = "No collection activity.",
}: CollectionsHistoryTableProps) {
  if (activities.length === 0) {
    return (
      <div
        className="rounded-lg border border-dashed border-slate-200 px-6 py-12 text-center dark:border-slate-800"
        data-testid="collections-history-empty"
      >
        <p className="text-sm text-slate-500 dark:text-slate-400">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Stage
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Status
            </th>
            <th className="hidden px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase sm:table-cell dark:text-slate-400">
              Overdue at action
            </th>
            <th className="hidden px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase md:table-cell dark:text-slate-400">
              Proposed
            </th>
            <th className="hidden px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase lg:table-cell dark:text-slate-400">
              Updated
            </th>
            <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Gate
            </th>
          </tr>
        </thead>
        <tbody>
          {activities.map((activity) => {
            const showReviewLink = activity.status === "PROPOSED" && activity.gateId !== null;
            return (
              <tr
                key={activity.id}
                className="group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
              >
                <td className="px-4 py-3 text-sm font-medium text-slate-900 dark:text-slate-100">
                  {formatCollectionStage(activity.stage)}
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-col gap-0.5">
                    <Badge variant={activityBadgeVariant(activity.status)}>
                      {formatActivityStatus(activity.status)}
                    </Badge>
                    {activity.reason && (
                      <span className="text-xs text-slate-500 dark:text-slate-400">
                        {formatSnakeCase(activity.reason)}
                      </span>
                    )}
                  </div>
                </td>
                <td className="hidden px-4 py-3 text-right font-mono text-sm text-slate-700 tabular-nums sm:table-cell dark:text-slate-300">
                  {formatDaysOverdue(activity.daysOverdueAtAction)}
                </td>
                <td className="hidden px-4 py-3 text-left font-mono text-xs text-slate-500 tabular-nums md:table-cell dark:text-slate-500">
                  {formatDateTime(activity.createdAt)}
                </td>
                <td className="hidden px-4 py-3 text-left font-mono text-xs text-slate-500 tabular-nums lg:table-cell dark:text-slate-500">
                  {formatDateTime(activity.updatedAt)}
                </td>
                <td className="px-4 py-3 text-right">
                  {showReviewLink ? (
                    <Link
                      href={`/org/${slug}/ai/reviews`}
                      className="text-sm font-medium text-teal-600 hover:underline dark:text-teal-400"
                    >
                      Review
                    </Link>
                  ) : (
                    <span className="text-xs text-slate-400 dark:text-slate-500">—</span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Maps a collection-activity status to a Badge variant. Mirrors the precedent in
 * 591B's `collections-client.tsx` (`activityBadgeVariant`) and extends it to the
 * full ledger status set:
 *   SENT → success; SEND_FAILED / FLAGGED → destructive; PROPOSED → warning;
 *   REJECTED / CANCELLED_PAYMENT / SKIPPED (and any unknown) → neutral.
 */
function activityBadgeVariant(status: string) {
  switch (status) {
    case "SENT":
      return "success" as const;
    case "SEND_FAILED":
    case "FLAGGED":
      return "destructive" as const;
    case "PROPOSED":
      return "warning" as const;
    default:
      return "neutral" as const;
  }
}

function formatDaysOverdue(days: number): string {
  if (days > 0) return `${days}d overdue`;
  if (days === 0) return "Due today";
  return `${Math.abs(days)}d until due`;
}

/**
 * Generic `SNAKE_CASE`/`snake_case` → "Title Case" formatter. Used for both the
 * status label and the machine-readable `reason`, so newly-added backend
 * statuses/reasons render sensibly instead of being dropped by an enum-keyed
 * lookup.
 */
function formatSnakeCase(value: string): string {
  return value
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

const formatActivityStatus = formatSnakeCase;
const formatCollectionStage = formatSnakeCase;
