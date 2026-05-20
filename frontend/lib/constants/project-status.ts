import type { ProjectStatus } from "@/lib/types";

export const PROJECT_STATUS_BADGE: Record<
  ProjectStatus,
  { label: string; variant: "success" | "warning" | "neutral" }
> = {
  ACTIVE: { label: "Active", variant: "success" },
  COMPLETED: { label: "Completed", variant: "neutral" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
  CLOSED: { label: "Closed", variant: "neutral" },
};
