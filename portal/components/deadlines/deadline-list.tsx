"use client";

import { CalendarClock } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  PortalDeadline,
  PortalDeadlineStatus,
  PortalDeadlineType,
} from "@/lib/api/deadlines";

const STATUS_FILTER_OPTIONS: Array<{
  value: "ALL" | PortalDeadlineStatus;
  label: string;
}> = [
  { value: "ALL", label: "All statuses" },
  { value: "UPCOMING", label: "Upcoming" },
  { value: "DUE_SOON", label: "Due soon" },
  { value: "OVERDUE", label: "Overdue" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
];

const TYPE_FILTER_OPTIONS: Array<{
  value: "ALL" | PortalDeadlineType;
  label: string;
}> = [
  { value: "ALL", label: "All types" },
  { value: "FILING", label: "Filing" },
  { value: "COURT_DATE", label: "Court date" },
  { value: "PRESCRIPTION", label: "Prescription" },
  { value: "CUSTOM_DATE", label: "Custom date" },
];

const DEADLINE_TYPE_LABEL: Record<string, string> = {
  FILING: "Filing",
  COURT_DATE: "Court date",
  PRESCRIPTION: "Prescription",
  CUSTOM_DATE: "Custom date",
};

type UrgencyTone = "grey" | "amber" | "red" | "red-solid" | "green";

/**
 * Maps a deadline to an urgency tone:
 * - COMPLETED / CANCELLED → green
 * - overdue (due < today) → red-solid
 * - due within 7 days     → red
 * - due within 14 days    → amber
 * - otherwise             → grey
 */
export function urgencyToneFor(
  deadline: PortalDeadline,
  today: Date = new Date(),
): UrgencyTone {
  if (deadline.status === "COMPLETED" || deadline.status === "CANCELLED") {
    return "green";
  }
  const due = new Date(`${deadline.dueDate}T00:00:00`);
  const startOfToday = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate(),
  );
  const diffMs = due.getTime() - startOfToday.getTime();
  const days = Math.floor(diffMs / 86_400_000);
  if (days < 0) return "red-solid";
  if (days <= 7) return "red";
  if (days <= 14) return "amber";
  return "grey";
}

const URGENCY_TEXT_CLASS: Record<UrgencyTone, string> = {
  grey: "text-slate-600 dark:text-slate-400",
  amber: "text-amber-600 dark:text-amber-400",
  red: "text-red-600 dark:text-red-400",
  "red-solid":
    "rounded-md bg-red-600 px-2 py-0.5 text-white dark:bg-red-700",
  green: "text-green-700 dark:text-green-300",
};

const STATUS_BADGE_CLASS: Record<string, string> = {
  UPCOMING:
    "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
  DUE_SOON:
    "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
  OVERDUE: "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300",
  COMPLETED:
    "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  CANCELLED:
    "bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400",
};

function formatStatus(status: string): string {
  return status.replace(/_/g, " ");
}

/**
 * Returns the ISO date ("YYYY-MM-DD") of the Monday of the week containing
 * `iso`. Uses local time to align with the portal's per-day rendering.
 */
function weekStart(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  const day = d.getDay(); // 0 = Sun, 1 = Mon, ..., 6 = Sat
  const diff = day === 0 ? -6 : 1 - day; // shift to Monday
  d.setDate(d.getDate() + diff);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${dd}`;
}

interface WeekGroup {
  weekStart: string;
  deadlines: PortalDeadline[];
}

function groupByWeek(items: PortalDeadline[]): WeekGroup[] {
  const byWeek = new Map<string, PortalDeadline[]>();
  for (const item of items) {
    const k = weekStart(item.dueDate);
    const bucket = byWeek.get(k) ?? [];
    bucket.push(item);
    byWeek.set(k, bucket);
  }
  // Preserve backend ordering (ascending by due date → ascending by week).
  return Array.from(byWeek.entries()).map(([w, deadlines]) => ({
    weekStart: w,
    deadlines,
  }));
}

interface DeadlineListProps {
  deadlines: PortalDeadline[] | null;
  isLoading: boolean;
  error: string | null;
  statusFilter: "ALL" | PortalDeadlineStatus;
  onStatusFilterChange: (value: "ALL" | PortalDeadlineStatus) => void;
  typeFilter: "ALL" | PortalDeadlineType;
  onTypeFilterChange: (value: "ALL" | PortalDeadlineType) => void;
  onSelect: (deadline: PortalDeadline) => void;
  selectedKey?: string | null;
  /** Optional override for the "now" clock — used by tests for deterministic urgency. */
  now?: Date;
}

/**
 * Deadline list grouped by week (Monday start). Backend returns rows ordered
 * by due date ascending, so groups render oldest → newest.
 *
 * The `type` filter is applied client-side (the backend only accepts a
 * status filter). The row click invokes `onSelect(deadline)` so the parent
 * can open a detail panel.
 */
export function DeadlineList({
  deadlines,
  isLoading,
  error,
  statusFilter,
  onStatusFilterChange,
  typeFilter,
  onTypeFilterChange,
  onSelect,
  selectedKey = null,
  now,
}: DeadlineListProps) {
  const filtered = (deadlines ?? []).filter(
    (d) => typeFilter === "ALL" || d.deadlineType === typeFilter,
  );
  const groups = groupByWeek(filtered);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2">
          <label
            htmlFor="deadline-status-filter"
            className="text-sm font-medium text-slate-700"
          >
            Status
          </label>
          <select
            id="deadline-status-filter"
            value={statusFilter}
            onChange={(e) =>
              onStatusFilterChange(
                e.target.value as "ALL" | PortalDeadlineStatus,
              )
            }
            className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
          >
            {STATUS_FILTER_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex items-center gap-2">
          <label
            htmlFor="deadline-type-filter"
            className="text-sm font-medium text-slate-700"
          >
            Type
          </label>
          <select
            id="deadline-type-filter"
            value={typeFilter}
            onChange={(e) =>
              onTypeFilterChange(e.target.value as "ALL" | PortalDeadlineType)
            }
            className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
          >
            {TYPE_FILTER_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {isLoading && (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      )}

      {!isLoading && error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {!isLoading && !error && filtered.length === 0 && (
        <div
          className="flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white py-12 text-center"
          data-testid="deadline-list-empty"
        >
          <CalendarClock
            className="mb-3 size-10 text-slate-300"
            aria-hidden="true"
          />
          <p className="text-sm font-medium text-slate-600">
            No deadlines in this view
          </p>
          <p className="mt-1 text-xs text-slate-500">
            Deadlines your firm surfaces to the portal will appear here.
          </p>
        </div>
      )}

      {!isLoading && !error && groups.length > 0 && (
        <div className="space-y-6" aria-label="Deadlines grouped by week">
          {groups.map((group) => (
            <section
              key={group.weekStart}
              aria-label={`Week of ${formatDate(group.weekStart)}`}
              data-testid={`deadline-week-${group.weekStart}`}
            >
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                Week of {formatDate(group.weekStart)}
              </h3>
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
                {group.deadlines.map((d) => {
                  const tone = urgencyToneFor(d, now);
                  const rowKey = `${d.sourceEntity}-${d.id}`;
                  const isSelected = rowKey === selectedKey;
                  return (
                    <li key={rowKey}>
                      <button
                        type="button"
                        onClick={() => onSelect(d)}
                        aria-label={`View details for ${d.label}`}
                        data-testid={`deadline-row-${rowKey}`}
                        data-tone={tone}
                        aria-pressed={isSelected}
                        className={cn(
                          "flex w-full items-start justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-slate-50 focus:outline-none focus:ring-1 focus:ring-teal-500",
                          isSelected && "bg-teal-50",
                        )}
                      >
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium text-slate-900">
                            {d.label}
                          </p>
                          <p className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                            <span>
                              {DEADLINE_TYPE_LABEL[d.deadlineType] ??
                                d.deadlineType}
                            </span>
                            <span
                              className={cn(
                                "inline-flex rounded-full px-2 py-0.5 text-xs font-medium",
                                STATUS_BADGE_CLASS[d.status] ??
                                  STATUS_BADGE_CLASS.UPCOMING,
                              )}
                            >
                              {formatStatus(d.status)}
                            </span>
                          </p>
                        </div>
                        <span
                          className={cn(
                            "shrink-0 font-mono text-sm font-semibold tabular-nums",
                            URGENCY_TEXT_CLASS[tone],
                          )}
                        >
                          {formatDate(d.dueDate)}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
