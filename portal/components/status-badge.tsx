import { Badge } from "@b2mash/ui/badge";
import { cn, humaniseStatus } from "@/lib/utils";

/** Neutral slate classes for a project status the colour map does not know yet. */
const NEUTRAL_STATUS_COLORS =
  "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400";

const PROJECT_STATUS_COLORS: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  CLOSED: "bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-400",
  COMPLETED: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  ARCHIVED: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
  ON_HOLD: "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
  CANCELLED:
    "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
};

// Phase 88 624H (G-D19): clients read "Closed", not "CLOSED". Task badges keep their
// raw enum text (formatStatus below).
const PROJECT_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "Active",
  CLOSED: "Closed",
  COMPLETED: "Completed",
  ARCHIVED: "Archived",
  ON_HOLD: "On hold",
  CANCELLED: "Cancelled",
};

const TASK_STATUS_COLORS: Record<string, string> = {
  OPEN: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
  IN_PROGRESS: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  REVIEW: "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
  DONE: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
};

const VARIANT_MAP: Record<string, Record<string, string>> = {
  project: PROJECT_STATUS_COLORS,
  task: TASK_STATUS_COLORS,
};

interface StatusBadgeProps {
  status: string;
  variant?: "project" | "task";
  className?: string;
}

function formatStatus(status: string): string {
  return status.replace(/_/g, " ");
}

export function StatusBadge({
  status,
  variant = "project",
  className,
}: StatusBadgeProps) {
  // The API guarantees a status; never let a missing one blank the whole list.
  if (!status) return null;

  const isProject = variant === "project";
  const colorMap = VARIANT_MAP[variant] ?? PROJECT_STATUS_COLORS;
  const colorClasses =
    colorMap[status] ??
    (isProject ? NEUTRAL_STATUS_COLORS : colorMap["OPEN"]) ??
    "";
  const label = isProject
    ? (PROJECT_STATUS_LABELS[status] ?? humaniseStatus(status))
    : formatStatus(status);

  return <Badge className={cn(colorClasses, className)}>{label}</Badge>;
}
