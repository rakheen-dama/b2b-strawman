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
