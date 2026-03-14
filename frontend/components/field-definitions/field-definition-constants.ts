import type { EntityType, FieldType } from "@/lib/types";

export const ENTITY_TYPES: { value: EntityType; label: string }[] = [
  { value: "PROJECT", label: "Projects" },
  { value: "TASK", label: "Tasks" },
  { value: "CUSTOMER", label: "Customers" },
];

export const FIELD_TYPES: { value: FieldType; label: string }[] = [
  { value: "TEXT", label: "Text" },
  { value: "NUMBER", label: "Number" },
  { value: "DATE", label: "Date" },
  { value: "DROPDOWN", label: "Dropdown" },
  { value: "BOOLEAN", label: "Boolean" },
  { value: "CURRENCY", label: "Currency" },
  { value: "URL", label: "URL" },
  { value: "EMAIL", label: "Email" },
  { value: "PHONE", label: "Phone" },
];

export type ConditionOperator = "eq" | "neq" | "in";

export const CONDITION_OPERATORS: { value: ConditionOperator; label: string }[] = [
  { value: "eq", label: "equals" },
  { value: "neq", label: "does not equal" },
  { value: "in", label: "is one of" },
];
