export interface UpdateDataProtectionSettingsRequest {
  dataProtectionJurisdiction?: string | null;
  retentionPolicyEnabled?: boolean;
  defaultRetentionMonths?: number | null;
  financialRetentionMonths?: number;
  informationOfficerName?: string | null;
  informationOfficerEmail?: string | null;
  /** Phase 88 U17 (ADR-354): 1–30 whole years; null or absent leaves the stored value. Owner only. */
  legalMatterRetentionYears?: number | null;
}

export interface DsarRequest {
  id: string;
  clientId: string | null;
  clientName: string | null;
  requestType: "ACCESS" | "DELETION" | "CORRECTION" | "OBJECTION" | "PORTABILITY";
  status: "RECEIVED" | "IN_PROGRESS" | "COMPLETED" | "REJECTED";
  subjectName: string | null;
  subjectEmail: string | null;
  description: string | null;
  resolutionNotes: string | null;
  deadline: string; // "YYYY-MM-DD" local date
  deadlineStatus: "ON_TRACK" | "DUE_SOON" | "OVERDUE";
  requestedAt: string; // ISO instant
  requestedBy: string;
  completedAt: string | null;
  jurisdiction: string | null;
  notes: string | null;
  createdAt: string;
}

export interface RetentionPolicyExtended {
  id: string;
  recordType: string;
  retentionDays: number;
  triggerEvent: string;
  action: string;
  active: boolean;
  description: string | null;
  lastEvaluatedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProcessingActivity {
  id: string;
  category: string;
  description: string;
  legalBasis: string;
  dataSubjects: string;
  retentionPeriod: string;
  recipients: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProcessingActivityRequest {
  category: string;
  description: string;
  legalBasis: string;
  dataSubjects: string;
  retentionPeriod: string;
  recipients?: string;
}

export interface RetentionEvaluationResult {
  totalPoliciesEvaluated: number;
  entitiesEligibleForPurge: number;
  policySummaries: PolicySummary[];
}

export interface PolicySummary {
  recordType: string;
  triggerEvent: string;
  action: string;
  eligibleCount: number;
}

export interface RetentionExecuteResult {
  totalPurged: number;
  totalFailed: number;
  executedAt: string;
}

export interface PaiaGenerateResponse {
  id: string;
  fileName: string;
  fileSize: number;
  generatedAt: string;
}

// ---- Standalone Client Data Protection (Epic 380A) ----

/** `POST /api/clients/{id}/anonymize` body (`AnonymizationController.AnonymizationRequest`). */
export interface AnonymizeClientRequest {
  confirmationName: string;
  reason: string;
}

/** `GET /api/clients/{id}/anonymize/preview` — flat backend record `datarequest/AnonymizationPreview` (ADR-365). */
export interface AnonymizationPreview {
  clientId: string;
  clientName: string;
  portalContacts: number;
  projects: number;
  documents: number;
  timeEntries: number;
  invoices: number;
  comments: number;
  customFieldValues: number;
  financialRecordsRetained: number;
  financialRetentionExpiresAt: string | null;
}

/** `POST /api/clients/{id}/data-export` (200) and `GET /api/data-exports/{id}` — `datarequest/ExportStatusResponse` (ADR-365). */
export interface StandaloneExportResult {
  exportId: string;
  status: string;
  downloadUrl: string;
  expiresAt: string;
  fileCount: number;
  totalSizeBytes: number;
  /** Storage key of the archive; sent by the backend, not rendered by the dialog. */
  s3Key: string;
}

/** `POST /api/clients/{id}/anonymize` — flat backend record `DataAnonymizationService.AnonymizationResult` (ADR-365). */
export interface StandaloneAnonymizationResult {
  customerAnonymized: boolean;
  documentsDeleted: number;
  commentsRedacted: number;
  portalContactsAnonymized: number;
  invoicesPreserved: number;
}
