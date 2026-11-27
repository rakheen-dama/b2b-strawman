import {
  CalendarCheck2,
  CalendarClock,
  CalendarDays,
  CalendarX2,
  type LucideIcon,
} from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { cn } from "@/lib/utils";
import type { CourtDateStatus } from "@/lib/types";

/**
 * Court-date status as icon + label (Phase 91 E8A, §18.2.6): the labels are the court-date list's
 * own (X3 — no new words); the icon means colour is never the only signal (§15.6.1). Shared by the
 * matter's court-date list and the Diary rows (which show it only when not SCHEDULED).
 */
const STATUS: Record<
  CourtDateStatus,
  {
    label: string;
    icon: LucideIcon;
    variant?: "warning" | "success" | "neutral";
    className?: string;
  }
> = {
  // The court-date list's one-off blue, kept as is (no colour change in this slice).
  SCHEDULED: {
    label: "Scheduled",
    icon: CalendarDays,
    className: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  },
  POSTPONED: { label: "Postponed", icon: CalendarClock, variant: "warning" },
  HEARD: { label: "Heard", icon: CalendarCheck2, variant: "success" },
  CANCELLED: { label: "Cancelled", icon: CalendarX2, variant: "neutral" },
};

function isCourtDateStatus(status: string): status is CourtDateStatus {
  return Object.prototype.hasOwnProperty.call(STATUS, status);
}

interface CourtDateStatusBadgeProps {
  /** A court-date status; the diary feed types it as a plain string. */
  status: string;
  className?: string;
}

export function CourtDateStatusBadge({ status, className }: CourtDateStatusBadgeProps) {
  // Unknown value: the raw status, neutral, no icon — the old inline helper's `default:` branch.
  if (!isCourtDateStatus(status)) {
    return (
      <Badge variant="neutral" className={className}>
        {status}
      </Badge>
    );
  }
  const { label, icon: Icon, variant, className: tone } = STATUS[status];
  return (
    <Badge variant={variant} className={cn(tone, className)} data-status={status}>
      <Icon aria-hidden="true" />
      {label}
    </Badge>
  );
}
