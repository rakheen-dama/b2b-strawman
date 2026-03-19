export interface UpdateDataProtectionSettingsRequest {
  dataProtectionJurisdiction?: string | null;
  retentionPolicyEnabled?: boolean;
  defaultRetentionMonths?: number | null;
  financialRetentionMonths?: number;
  informationOfficerName?: string | null;
  informationOfficerEmail?: string | null;
}

export interface DsarRequest {
  id: string;
  customerId: string | null;
  customerName: string | null;
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

// ---- Standalone Customer Data Protection (Epic 380A) ----

export interface AnonymizationPreview {
  customerId: string;
  customerName: string;
  affectedEntities: {
    portalContacts: number;
    projects: number;
    documents: number;
    timeEntries: number;
    invoices: number;
    comments: number;
    customFieldValues: number;
  };
  financialRecordsRetained: number;
  financialRetentionExpiresAt: string | null;
}

export interface StandaloneExportResult {
  exportId: string;
  status: string;
  downloadUrl: string;
  expiresAt: string;
  fileCount: number;
  totalSizeBytes: number;
}

export interface StandaloneAnonymizationResult {
  status: "COMPLETED";
  referenceId: string;
  preExportKey: string;
  summary: {
    customerAnonymized: boolean;
    documentsDeleted: number;
    commentsRedacted: number;
    portalContactsAnonymized: number;
    invoicesPreserved: number;
    customFieldsCleared: number;
  };
}
