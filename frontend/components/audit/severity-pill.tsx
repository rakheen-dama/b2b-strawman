import { cn } from "@/lib/utils";
import type { AuditSeverity } from "@/lib/api/audit-events";

const SEVERITY_CLASSES: Record<AuditSeverity, string> = {
  INFO: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
  NOTICE: "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300",
  WARNING: "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300",
  CRITICAL: "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300",
};

const SIZE_CLASSES = {
  sm: "text-[10px] px-1.5 py-0.5",
  md: "text-xs px-2 py-0.5",
} as const;

export interface SeverityPillProps {
  severity: AuditSeverity;
  size?: "sm" | "md";
  className?: string;
}

export function SeverityPill({
  severity,
  size = "md",
  className,
}: SeverityPillProps) {
  return (
    <span
      data-testid="severity-pill"
      data-severity={severity}
      className={cn(
        "inline-flex items-center rounded-full font-medium",
        SIZE_CLASSES[size],
        SEVERITY_CLASSES[severity],
        className,
      )}
    >
      {severity}
    </span>
  );
}
