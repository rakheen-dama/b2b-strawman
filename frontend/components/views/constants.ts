import type { EntityType } from "@/lib/types";
import { MATTER_COLUMNS } from "@/lib/matters-list";

/** Standard (non-custom-field) columns per entity type, used by Create and Edit view dialogs. */
export const STANDARD_COLUMNS: Record<EntityType, { value: string; label: string }[]> = {
  // The Matters table's own columns (615C) — single source in lib/matters-list.ts.
  PROJECT: MATTER_COLUMNS.map((c) => ({ value: c.id, label: c.label })),
  CUSTOMER: [
    { value: "name", label: "Name" },
    { value: "email", label: "Email" },
    { value: "phone", label: "Phone" },
    { value: "status", label: "Status" },
    { value: "createdAt", label: "Created At" },
  ],
  TASK: [
    { value: "title", label: "Title" },
    { value: "status", label: "Status" },
    { value: "priority", label: "Priority" },
    { value: "assigneeName", label: "Assignee" },
    { value: "dueDate", label: "Due Date" },
    { value: "createdAt", label: "Created At" },
  ],
  INVOICE: [
    { value: "invoiceNumber", label: "Invoice Number" },
    { value: "customerName", label: "Customer" },
    { value: "status", label: "Status" },
    { value: "total", label: "Total" },
    { value: "issueDate", label: "Issue Date" },
    { value: "dueDate", label: "Due Date" },
    { value: "createdAt", label: "Created At" },
  ],
};
