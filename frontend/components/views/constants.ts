import type { EntityType } from "@/lib/types";

/** Standard (non-custom-field) columns per entity type, used by Create and Edit view dialogs. */
export const STANDARD_COLUMNS: Record<EntityType, { value: string; label: string }[]> = {
  PROJECT: [
    { value: "name", label: "Name" },
    { value: "description", label: "Description" },
    { value: "createdAt", label: "Created At" },
    { value: "updatedAt", label: "Updated At" },
  ],
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
};
