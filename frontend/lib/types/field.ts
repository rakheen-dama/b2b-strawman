// ---- Custom Fields (from FieldDefinitionController, FieldGroupController) ----

export type EntityType = "PROJECT" | "TASK" | "CUSTOMER" | "INVOICE";
export type FieldType =
  | "TEXT"
  | "NUMBER"
  | "DATE"
  | "DROPDOWN"
  | "BOOLEAN"
  | "CURRENCY"
  | "URL"
  | "EMAIL"
  | "PHONE";

/** Shared visibility condition used by field definitions and intake fields */
export interface VisibilityCondition {
  dependsOnSlug: string;
  operator: string;
  value: string | string[];
}

export interface FieldDefinitionResponse {
  id: string;
  entityType: EntityType;
  name: string;
  slug: string;
  fieldType: FieldType;
  description: string | null;
  required: boolean;
  defaultValue: Record<string, unknown> | null;
  options: Array<{ value: string; label: string }> | null;
  validation: Record<string, unknown> | null;
  sortOrder: number;
  packId: string | null;
  packFieldKey: string | null;
  visibilityCondition: VisibilityCondition | null;
  requiredForContexts: string[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
  /** When true, DATE values of this field surface on the portal Deadlines page. */
  portalVisibleDeadline: boolean;
}

export interface CreateFieldDefinitionRequest {
  entityType: EntityType;
  name: string;
  slug?: string;
  fieldType: FieldType;
  description?: string;
  required: boolean;
  defaultValue?: Record<string, unknown>;
  options?: Array<{ value: string; label: string }>;
  validation?: Record<string, unknown>;
  sortOrder: number;
  visibilityCondition?: VisibilityCondition | null;
  requiredForContexts?: string[];
  /** Only meaningful for DATE fields; ignored otherwise. */
  portalVisibleDeadline?: boolean;
}

export interface UpdateFieldDefinitionRequest {
  name: string;
  slug?: string;
  fieldType: FieldType;
  description?: string;
  required: boolean;
  defaultValue?: Record<string, unknown>;
  options?: Array<{ value: string; label: string }>;
  validation?: Record<string, unknown>;
  sortOrder: number;
  visibilityCondition?: VisibilityCondition | null;
  requiredForContexts?: string[];
  /** Only meaningful for DATE fields; ignored otherwise. */
  portalVisibleDeadline?: boolean;
}

export interface FieldGroupResponse {
  id: string;
  entityType: EntityType;
  name: string;
  slug: string;
  description: string | null;
  packId: string | null;
  sortOrder: number;
  active: boolean;
  autoApply: boolean;
  dependsOn: string[] | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFieldGroupRequest {
  entityType: EntityType;
  name: string;
  slug?: string;
  description?: string;
  sortOrder: number;
  fieldDefinitionIds: string[];
  autoApply?: boolean;
  dependsOn?: string[];
}

export interface UpdateFieldGroupRequest {
  name: string;
  slug?: string;
  description?: string;
  sortOrder: number;
  fieldDefinitionIds: string[];
  autoApply?: boolean;
  dependsOn?: string[];
}

export interface FieldGroupMemberResponse {
  id: string;
  fieldGroupId: string;
  fieldDefinitionId: string;
  sortOrder: number;
}

// ---- Saved Views (from SavedViewController) ----

export interface SavedViewResponse {
  id: string;
  entityType: EntityType;
  name: string;
  filters: Record<string, unknown>;
  columns: string[] | null;
  shared: boolean;
  createdBy: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSavedViewRequest {
  entityType: EntityType;
  name: string;
  filters: Record<string, unknown>;
  columns?: string[];
  shared: boolean;
  sortOrder: number;
}

export interface UpdateSavedViewRequest {
  name: string;
  filters: Record<string, unknown>;
  columns?: string[];
  sortOrder: number;
}
