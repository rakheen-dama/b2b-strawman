// ---- Customers (from CustomerController.java) ----

import type { TagResponse } from "./common";

export type CustomerStatus = "ACTIVE" | "ARCHIVED";

export type LifecycleStatus =
  | "PROSPECT"
  | "ONBOARDING"
  | "ACTIVE"
  | "DORMANT"
  | "OFFBOARDING"
  | "OFFBOARDED"
  | "ANONYMIZED";

export type CustomerType = "INDIVIDUAL" | "COMPANY" | "TRUST";

export interface Customer {
  id: string;
  name: string;
  email: string;
  phone: string | null;
  idNumber: string | null;
  status: CustomerStatus;
  notes: string | null;
  createdBy: string;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
  customFields?: Record<string, unknown>;
  appliedFieldGroups?: string[];
  tags?: TagResponse[];
  lifecycleStatus?: LifecycleStatus;
  customerType?: string;
  lifecycleStatusChangedAt?: string | null;
  // Promoted customer fields (Epic 463 / Phase 63)
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  stateProvince?: string | null;
  postalCode?: string | null;
  country?: string | null;
  taxNumber?: string | null;
  contactName?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  registrationNumber?: string | null;
  entityType?: string | null;
  financialYearEnd?: string | null;
}

export interface TransitionResponse {
  id: string;
  name: string;
  lifecycleStatus: LifecycleStatus;
  lifecycleStatusChangedAt: string;
  lifecycleStatusChangedBy: string;
  lifecycleStatusChangedByName: string | null;
}

export interface LifecycleHistoryEntry {
  id: string;
  eventType: string;
  entityType: string;
  entityId: string;
  actorId: string | null;
  actorType: string;
  source: string;
  details: Record<string, unknown> | null;
  occurredAt: string;
}

export interface CreateCustomerRequest {
  name: string;
  email: string;
  phone?: string;
  idNumber?: string;
  notes?: string;
  customerType?: CustomerType;
  // Promoted customer fields (Epic 463 / Phase 63)
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  stateProvince?: string;
  postalCode?: string;
  country?: string;
  taxNumber?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  registrationNumber?: string;
  entityType?: string;
  financialYearEnd?: string;
}

export interface UpdateCustomerRequest {
  name: string;
  email: string;
  phone?: string;
  idNumber?: string;
  notes?: string;
  customerType?: CustomerType;
  // Promoted customer fields (Epic 463 / Phase 63)
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  stateProvince?: string;
  postalCode?: string;
  country?: string;
  taxNumber?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  registrationNumber?: string;
  entityType?: string;
  financialYearEnd?: string;
}

export interface CustomerProject {
  customerId: string;
  projectId: string;
  linkedBy: string | null;
  createdAt: string;
}

// ---- Checklist Templates (from ChecklistTemplateController.java) ----

export interface ChecklistTemplateItemResponse {
  id: string;
  templateId: string;
  name: string;
  description: string | null;
  sortOrder: number;
  required: boolean;
  requiresDocument: boolean;
  requiredDocumentLabel: string | null;
  dependsOnItemId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ChecklistTemplateResponse {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  customerType: string;
  source: string;
  packId: string | null;
  active: boolean;
  autoInstantiate: boolean;
  sortOrder: number;
  items: ChecklistTemplateItemResponse[];
  createdAt: string;
  updatedAt: string;
}

// ---- Checklist Instances (from ChecklistInstanceController.java) ----

export type ChecklistItemStatus = "PENDING" | "COMPLETED" | "SKIPPED" | "BLOCKED" | "CANCELLED";
export type ChecklistInstanceStatus = "IN_PROGRESS" | "COMPLETED" | "CANCELLED";

export interface ChecklistInstanceItemResponse {
  id: string;
  instanceId: string;
  templateItemId: string;
  name: string;
  description: string | null;
  sortOrder: number;
  required: boolean;
  requiresDocument: boolean;
  requiredDocumentLabel: string | null;
  status: ChecklistItemStatus;
  completedAt: string | null;
  completedBy: string | null;
  completedByName: string | null;
  notes: string | null;
  documentId: string | null;
  dependsOnItemId: string | null;
  verificationProvider?: string | null;
  verificationReference?: string | null;
  verificationStatus?: string | null;
  verifiedAt?: string | null;
  verificationMetadata?: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
}

export interface ChecklistInstanceResponse {
  id: string;
  templateId: string;
  customerId: string;
  status: ChecklistInstanceStatus;
  startedAt: string;
  completedAt: string | null;
  completedBy: string | null;
  completedByName: string | null;
  items: ChecklistInstanceItemResponse[];
  createdAt: string;
  updatedAt: string;
}

// ---- Data Requests (from DataRequestController.java) ----

export type DataRequestStatus = "RECEIVED" | "IN_PROGRESS" | "COMPLETED" | "REJECTED";
export type DataRequestType = "ACCESS" | "DELETION" | "CORRECTION" | "OBJECTION";

export interface DataRequestResponse {
  id: string;
  customerId: string;
  customerName: string;
  requestType: DataRequestType;
  status: DataRequestStatus;
  description: string;
  rejectionReason: string | null;
  deadline: string;          // "YYYY-MM-DD" local date
  requestedAt: string;       // ISO instant
  requestedBy: string;
  requestedByName: string | null;
  completedAt: string | null;
  completedBy: string | null;
  completedByName: string | null;
  hasExport: boolean;
  notes: string | null;
  createdAt: string;
}

export interface AnonymizationResult {
  status: "COMPLETED";
  anonymizationSummary: {
    customerAnonymized: boolean;
    documentsDeleted: number;
    commentsRedacted: number;
    portalContactsAnonymized: number;
    invoicesPreserved: number;
  };
}

// ---- Retention Policies (from RetentionController.java) ----

export interface RetentionPolicy {
  id: string;
  recordType: string; // "CUSTOMER" | "AUDIT_EVENT" | "DOCUMENT" | "COMMENT"
  retentionDays: number;
  triggerEvent: string; // "CUSTOMER_OFFBOARDED" | "RECORD_CREATED"
  action: string; // "FLAG" | "ANONYMIZE"
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRetentionPolicyRequest {
  recordType: string;
  retentionDays: number;
  triggerEvent: string;
  action: string;
}

export interface UpdateRetentionPolicyRequest {
  retentionDays: number;
  action: string;
  active: boolean;
}

export interface FlaggedRecords {
  count: number;
  recordType: string;
  triggerEvent: string;
  action: string;
  recordIds: string[];
}

export interface RetentionCheckResult {
  checkedAt: string;
  flagged: Record<string, FlaggedRecords>;
  totalFlagged: number;
}

export interface PurgeResult {
  recordType: string;
  purged: number;
  failed: number;
}

export interface CompliancePackEntry {
  packId: string;
  version: string;
  appliedAt: string;
}

export interface CompliancePackDetail {
  packId: string;
  name: string;
  description: string;
  version: string;
  jurisdiction: string | null;
  customerType: string;
  checklistTemplate: {
    name: string;
    slug: string;
    autoInstantiate: boolean;
    items: Array<{
      name: string;
      description: string;
      sortOrder: number;
      required: boolean;
      requiresDocument: boolean;
      requiredDocumentLabel: string | null;
      dependsOnItemKey: string | null;
    }>;
  } | null;
  fieldDefinitions: Array<{
    fieldKey: string;
    label: string;
    fieldType: string;
    required: boolean;
    options: string[];
    groupName: string | null;
  }> | null;
  retentionOverrides: Array<{
    recordType: string;
    triggerEvent: string;
    retentionDays: number;
    action: string;
  }> | null;
}

export interface UpdateComplianceSettingsRequest {
  dormancyThresholdDays?: number;
  dataRequestDeadlineDays?: number;
}
