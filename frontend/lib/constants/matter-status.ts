import type { MatterStatus } from "@/lib/types";

export const MATTER_STATUS_BADGE: Record<
  MatterStatus,
  { label: string; variant: "success" | "warning" | "neutral" }
> = {
  ACTIVE: { label: "Active", variant: "success" },
  COMPLETED: { label: "Completed", variant: "neutral" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
  CLOSED: { label: "Closed", variant: "neutral" },
};
