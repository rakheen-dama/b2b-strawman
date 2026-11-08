import type { DataRequestStatus } from "@/lib/types";

/**
 * Data-request (DSAR) status badges. A plain module, not "use client": the request detail page is a
 * Server Component, and importing a value from a client module gives it a client reference, so the
 * badge fell back to the raw status (G89-F20).
 */
export type BadgeVariant = "success" | "warning" | "destructive" | "neutral";

export interface StatusConfig {
  label: string;
  variant: BadgeVariant;
  className?: string;
}

export const STATUS_CONFIG: Record<DataRequestStatus, StatusConfig> = {
  RECEIVED: { label: "Received", variant: "neutral" },
  IN_PROGRESS: {
    label: "In Progress",
    variant: "neutral",
    className: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  },
  COMPLETED: { label: "Completed", variant: "success" },
  REJECTED: { label: "Rejected", variant: "destructive" },
};
