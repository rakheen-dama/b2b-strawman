import type { FieldType } from "@/lib/types";

/** Matches backend PrerequisiteContext enum */
export type PrerequisiteContext =
  | "LIFECYCLE_ACTIVATION"
  | "INVOICE_GENERATION"
  | "PROPOSAL_SEND"
  | "DOCUMENT_GENERATION"
  | "PROJECT_CREATION";

export const PREREQUISITE_CONTEXT_LABELS: Record<
  PrerequisiteContext,
  string
> = {
  LIFECYCLE_ACTIVATION: "Customer Activation",
  INVOICE_GENERATION: "Invoice Generation",
  PROPOSAL_SEND: "Proposal Sending",
  DOCUMENT_GENERATION: "Document Generation",
  PROJECT_CREATION: "Project Creation",
};

export interface PrerequisiteViolation {
  code: string;
  message: string;
  entityType: string;
  entityId: string;
  fieldSlug: string | null;
  groupName: string | null;
  resolution: string;
}

export interface PrerequisiteCheck {
  passed: boolean;
  context: string;
  violations: PrerequisiteViolation[];
}

/** Matches IntakeFieldGroupResponse.IntakeFieldResponse from backend */
export interface IntakeField {
  id: string;
  name: string;
  slug: string;
  fieldType: FieldType;
  required: boolean;
  description: string | null;
  options: Array<{ value: string; label: string }> | null;
  defaultValue: Record<string, unknown> | null;
  requiredForContexts: string[];
  visibilityCondition: {
    dependsOnSlug: string;
    operator: string;
    value: string | string[];
  } | null;
}

/** Matches IntakeFieldGroupResponse.GroupResponse from backend */
export interface IntakeFieldGroup {
  id: string;
  name: string;
  slug: string;
  fields: IntakeField[];
}

/** Response wrapper matching IntakeFieldGroupResponse from backend */
export interface IntakeFieldGroupsResponse {
  groups: IntakeFieldGroup[];
}
