export interface CalendarItem {
  id: string;
  name: string;
  itemType: "TASK" | "PROJECT";
  dueDate: string;
  status: string;
  priority: string | null;
  assigneeId: string | null;
  projectId: string;
  projectName: string;
}

export interface CalendarResponse {
  items: CalendarItem[];
  overdueCount: number;
}

export interface CalendarFilters {
  projectId?: string;
  type?: "TASK" | "PROJECT";
  assigneeId?: string;
  overdue?: boolean;
}

export function getStatusVariant(
  status: string
): "neutral" | "warning" | "success" | "secondary" {
  switch (status) {
    case "OPEN":
      return "neutral";
    case "IN_PROGRESS":
      return "warning";
    case "DONE":
      return "success";
    case "CANCELLED":
    case "ARCHIVED":
      return "secondary";
    case "ACTIVE":
      return "success";
    default:
      return "neutral";
  }
}

/**
 * Formats a Date as YYYY-MM-DD string.
 */
export function formatDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${dd}`;
}

/**
 * Returns the link path for a calendar item (task or project).
 */
export function getItemLink(item: CalendarItem, slug: string): string {
  if (item.itemType === "TASK") {
    return `/org/${slug}/projects/${item.projectId}?taskId=${item.id}`;
  }
  return `/org/${slug}/projects/${item.projectId}`;
}

function parseDueDateToDate(dateStr: string): Date {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/**
 * Returns urgency-based color classes for calendar items.
 * Overdue items get red, items due within 7 days get amber.
 * Completed/cancelled/archived items never show urgency.
 */
export function getDueDateColor(
  dueDate: string,
  status: string
): { dot: string; row: string } {
  if (["DONE", "CANCELLED", "ARCHIVED", "COMPLETED"].includes(status)) {
    return { dot: "", row: "" };
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const due = parseDueDateToDate(dueDate);

  if (due < today) {
    return {
      dot: "bg-red-500",
      row: "bg-red-50 dark:bg-red-950/20",
    };
  }

  const sevenDaysFromNow = new Date(today);
  sevenDaysFromNow.setDate(today.getDate() + 7);
  if (due <= sevenDaysFromNow) {
    return {
      dot: "bg-amber-500",
      row: "bg-amber-50 dark:bg-amber-950/20",
    };
  }

  return { dot: "", row: "" };
}
