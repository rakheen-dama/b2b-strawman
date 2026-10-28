import type { EntityType } from "@/lib/types";
import { MATTER_COLUMNS } from "@/lib/matters-list";
import { CLIENT_COLUMNS, migrateLegacyClientColumns } from "@/lib/clients-list";

/** Standard (non-custom-field) columns per entity type, used by Create and Edit view dialogs. */
export const STANDARD_COLUMNS: Record<EntityType, { value: string; label: string }[]> = {
  // The Matters table's own columns (615C) — single source in lib/matters-list.ts.
  PROJECT: MATTER_COLUMNS.map((c) => ({ value: c.id, label: c.label })),
  // The Clients table's own columns (615D) — single source in lib/clients-list.ts.
  CUSTOMER: CLIENT_COLUMNS.map((c) => ({ value: c.id, label: c.label })),
  TASK: [
    { value: "title", label: "Title" },
    { value: "status", label: "Status" },
    { value: "priority", label: "Priority" },
    { value: "assigneeName", label: "Assignee" },
    { value: "dueDate", label: "Due Date" },
    { value: "createdAt", label: "Created At" },
  ],
  INVOICE: [
    { value: "invoiceNumber", label: "Fee note number" },
    { value: "customerName", label: "Client" },
    { value: "status", label: "Status" },
    { value: "total", label: "Total" },
    { value: "issueDate", label: "Issue Date" },
    { value: "dueDate", label: "Due Date" },
    { value: "createdAt", label: "Created At" },
  ],
};

/**
 * A saved view's stored `columns` as the dialogs should show them: CUSTOMER views saved before
 * 615D carry `email` / `phone` / `status`, which map to the Clients table's ids (the same mapping
 * the table renders with). Other entity types are unchanged.
 */
export function normalizeViewColumns(entityType: EntityType, columns: readonly string[]): string[] {
  return entityType === "CUSTOMER" ? migrateLegacyClientColumns(columns) : [...columns];
}
