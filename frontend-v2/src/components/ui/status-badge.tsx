import { cva } from "class-variance-authority";

import { cn } from "@/lib/utils";

/**
 * Normalize a status string to a canonical UPPER_SNAKE_CASE key.
 * Handles spaces, underscores, mixed case, and extra whitespace.
 * e.g. "In Progress" | "in_progress" | "IN_PROGRESS" -> "IN_PROGRESS"
 */
function normalizeStatus(status: string): string {
  return status.trim().replace(/[\s_]+/g, "_").toUpperCase();
}

/**
 * Convert a normalized UPPER_SNAKE_CASE status to a human-readable label.
 * e.g. "IN_PROGRESS" -> "In Progress"
 */
function formatLabel(normalized: string): string {
  return normalized
    .split("_")
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(" ");
}

type StatusVariant = "slate" | "blue" | "emerald" | "amber" | "red" | "purple";

const STATUS_MAP: Record<string, StatusVariant> = {
  DRAFT: "slate",
  PENDING: "slate",
  PROSPECT: "slate",
  TODO: "slate",
  ORG: "slate",
  INTERNAL: "slate",

  IN_PROGRESS: "blue",
  ONBOARDING: "blue",
  SENT: "blue",
  PROJECT: "blue",

  ACTIVE: "emerald",
  COMPLETED: "emerald",
  PAID: "emerald",
  ON_TRACK: "emerald",
  DONE: "emerald",
  UPLOADED: "emerald",
  SHARED: "emerald",
  ACCEPTED: "emerald",

  AT_RISK: "amber",
  OVERDUE: "amber",
  DORMANT: "amber",
  EXPIRED: "amber",

  OVER_BUDGET: "red",
  CANCELLED: "red",
  VOID: "red",
  FAILED: "red",
  DECLINED: "red",

  ARCHIVED: "purple",
  OFFBOARDED: "purple",
  CUSTOMER: "purple",
};

const statusBadgeVariants = cva(
  "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium",
  {
    variants: {
      variant: {
        slate: "bg-slate-100 text-slate-700",
        blue: "bg-blue-100 text-blue-700",
        emerald: "bg-emerald-100 text-emerald-700",
        amber: "bg-amber-100 text-amber-700",
        red: "bg-red-100 text-red-700",
        purple: "bg-purple-100 text-purple-700",
      },
    },
    defaultVariants: {
      variant: "slate",
    },
  },
);

interface StatusBadgeProps {
  status: string;
  className?: string;
}

function StatusBadge({ status, className }: StatusBadgeProps) {
  const normalized = normalizeStatus(status);
  const variant = STATUS_MAP[normalized] ?? "slate";
  const label = formatLabel(normalized);

  return (
    <span className={cn(statusBadgeVariants({ variant }), className)}>
      {label}
    </span>
  );
}

export { StatusBadge, statusBadgeVariants };
export type { StatusBadgeProps };
