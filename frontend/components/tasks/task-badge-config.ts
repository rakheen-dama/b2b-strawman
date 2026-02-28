import type { TaskPriority, TaskStatus } from "@/lib/types";

// Priority badge config (40.8): HIGH=red, MEDIUM=amber, LOW=olive

export const PRIORITY_BADGE: Record<
  TaskPriority,
  { label: string; variant: "destructive" | "warning" | "neutral" }
> = {
  HIGH: { label: "High", variant: "destructive" },
  MEDIUM: { label: "Medium", variant: "warning" },
  LOW: { label: "Low", variant: "neutral" },
};

export const STATUS_BADGE: Record<
  TaskStatus,
  { label: string; variant: "success" | "warning" | "neutral" }
> = {
  OPEN: { label: "Open", variant: "neutral" },
  IN_PROGRESS: { label: "In Progress", variant: "warning" },
  DONE: { label: "Done", variant: "success" },
  CANCELLED: { label: "Cancelled", variant: "neutral" },
};
