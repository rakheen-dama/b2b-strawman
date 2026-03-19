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
