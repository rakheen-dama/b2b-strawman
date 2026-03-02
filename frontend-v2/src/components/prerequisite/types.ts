/**
 * Minimal subset of frontend/components/prerequisite/types.ts,
 * scoped to what frontend-v2 needs for prerequisite checking.
 * Keep in sync with the canonical types in the main frontend.
 */
export type PrerequisiteContext =
  | "LIFECYCLE_ACTIVATION"
  | "INVOICE_GENERATION"
  | "PROPOSAL_SEND"
  | "DOCUMENT_GENERATION"
  | "PROJECT_CREATION";

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
