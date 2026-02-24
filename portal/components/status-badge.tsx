import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const PROJECT_STATUS_COLORS: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  COMPLETED: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  ON_HOLD:
    "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
  CANCELLED:
    "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
};

const TASK_STATUS_COLORS: Record<string, string> = {
  OPEN: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
  IN_PROGRESS:
    "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  REVIEW:
    "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
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
  const colorMap = VARIANT_MAP[variant] ?? PROJECT_STATUS_COLORS;
  const colorClasses = colorMap[status] ?? colorMap["OPEN"] ?? "";

  return (
    <Badge className={cn(colorClasses, className)}>{formatStatus(status)}</Badge>
  );
}
