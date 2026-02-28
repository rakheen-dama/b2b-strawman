// ---- Projects (from ProjectController.java) ----

export type ProjectStatus = "ACTIVE" | "COMPLETED" | "ARCHIVED";

export interface Project {
  id: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  customerId: string | null;
  dueDate: string | null;
  createdBy: string;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  completedBy: string | null;
  completedByName: string | null;
  archivedAt: string | null;
  projectRole: ProjectRole | null;
  customFields?: Record<string, unknown>;
  appliedFieldGroups?: string[];
  tags?: TagResponse[];
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
  customerId?: string;
  dueDate?: string;
}

export interface UpdateProjectRequest {
  name: string;
  description?: string;
  customerId?: string | null;
  dueDate?: string | null;
}

// ---- Documents (from DocumentController.java) ----

export type DocumentStatus = "PENDING" | "UPLOADED" | "FAILED";

export type DocumentScope = "ORG" | "PROJECT" | "CUSTOMER";

export type DocumentVisibility = "INTERNAL" | "SHARED";

export interface Document {
  id: string;
  projectId: string | null;
  fileName: string;
  contentType: string;
  size: number;
  status: DocumentStatus;
  scope: DocumentScope;
  customerId: string | null;
  visibility: DocumentVisibility;
  uploadedBy: string | null;
  uploadedByName: string | null;
  uploadedAt: string | null;
  createdAt: string;
}

export interface UploadInitRequest {
  fileName: string;
  contentType: string;
  size: number;
}

export interface UploadInitResponse {
  documentId: string;
  presignedUrl: string;
  expiresInSeconds: number;
}

export interface PresignDownloadResponse {
  presignedUrl: string;
  expiresInSeconds: number;
}

// ---- Members (from OrgMemberController.java) ----

export interface OrgMember {
  id: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  orgRole: string;
}

// ---- Project Members (from ProjectMemberController.java) ----

export type ProjectRole = "lead" | "member";

export interface ProjectMember {
  id: string;
  memberId: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  projectRole: ProjectRole;
  orgRole: string;
  createdAt: string;
}

// ---- Customers (from CustomerController.java) ----

export type CustomerStatus = "ACTIVE" | "ARCHIVED";

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
}

export type LifecycleStatus =
  | "PROSPECT"
  | "ONBOARDING"
  | "ACTIVE"
  | "DORMANT"
  | "OFFBOARDING"
  | "OFFBOARDED";

export type CustomerType = "INDIVIDUAL" | "COMPANY" | "TRUST";

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
}

export interface UpdateCustomerRequest {
  name: string;
  email: string;
  phone?: string;
  idNumber?: string;
  notes?: string;
  customerType?: CustomerType;
}

export interface CustomerProject {
  customerId: string;
  projectId: string;
  linkedBy: string | null;
  createdAt: string;
}

// ---- Tasks (from TaskController.java) ----

export type TaskStatus = "OPEN" | "IN_PROGRESS" | "DONE" | "CANCELLED";

export type TaskPriority = "LOW" | "MEDIUM" | "HIGH";

export interface Task {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  type: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  createdBy: string;
  createdByName: string | null;
  dueDate: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  completedBy: string | null;
  completedByName: string | null;
  cancelledAt: string | null;
  customFields?: Record<string, unknown>;
  appliedFieldGroups?: string[];
  tags?: TagResponse[];
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  priority?: TaskPriority;
  type?: string;
  dueDate?: string;
  assigneeId?: string;
}

export interface UpdateTaskRequest {
  title: string;
  description?: string;
  priority: TaskPriority;
  status: TaskStatus;
  type?: string;
  dueDate?: string;
  assigneeId?: string;
}

// ---- Task Items (from TaskItemController.java) ----

export interface TaskItem {
  id: string;
  taskId: string;
  title: string;
  completed: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

// ---- Time Entries (from TimeEntryController.java) ----

export interface TimeEntry {
  id: string;
  taskId: string;
  memberId: string;
  memberName: string;
  date: string;
  durationMinutes: number;
  billable: boolean;
  rateCents: number | null;
  billingRateSnapshot: number | null;
  billingRateCurrency: string | null;
  costRateSnapshot: number | null;
  costRateCurrency: string | null;
  billableValue: number | null;
  costValue: number | null;
  description: string | null;
  invoiceId: string | null;
  invoiceNumber: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTimeEntryRequest {
  date: string;
  durationMinutes: number;
  billable?: boolean;
  rateCents?: number;
  description?: string;
}

export interface UpdateTimeEntryRequest {
  date?: string;
  durationMinutes?: number;
  billable?: boolean;
  rateCents?: number;
  description?: string;
}

// ---- Time Summaries (from ProjectTimeSummaryController.java) ----

export interface ProjectTimeSummary {
  billableMinutes: number;
  nonBillableMinutes: number;
  totalMinutes: number;
  contributorCount: number;
  entryCount: number;
}

export interface MemberTimeSummary {
  memberId: string;
  memberName: string;
  billableMinutes: number;
  nonBillableMinutes: number;
  totalMinutes: number;
}

export interface TaskTimeSummary {
  taskId: string;
  taskTitle: string;
  billableMinutes: number;
  totalMinutes: number;
  entryCount: number;
}

// ---- My Work (from MyWorkController.java) ----

export interface MyWorkTaskItem {
  id: string;
  projectId: string;
  projectName: string;
  title: string;
  status: string;
  priority: string;
  dueDate: string | null;
  totalTimeMinutes: number;
}

export interface MyWorkTasksResponse {
  assigned: MyWorkTaskItem[];
  unassigned: MyWorkTaskItem[];
}

export interface MyWorkTimeEntryItem {
  id: string;
  taskId: string;
  taskTitle: string;
  projectId: string;
  projectName: string;
  date: string;
  durationMinutes: number;
  billable: boolean;
  description: string | null;
}

export interface MyWorkProjectSummary {
  projectId: string;
  projectName: string;
  billableMinutes: number;
  nonBillableMinutes: number;
  totalMinutes: number;
}

export interface MyWorkTimeSummary {
  memberId: string;
  fromDate: string;
  toDate: string;
  billableMinutes: number;
  nonBillableMinutes: number;
  totalMinutes: number;
  byProject: MyWorkProjectSummary[];
}

// ---- Portal (from PortalAuthController, PortalProjectController, PortalDocumentController) ----

export interface PortalProject {
  id: string;
  name: string;
  description: string | null;
  documentCount: number;
}

export interface PortalDocument {
  id: string;
  fileName: string;
  contentType: string;
  size: number;
  scope: DocumentScope;
  projectId: string | null;
  projectName: string | null;
  uploadedAt: string | null;
  createdAt: string;
}

export interface PortalAuthResponse {
  token: string;
  customerName: string;
  expiresIn: number;
}

export interface MagicLinkResponse {
  message: string;
  magicLink?: string;
}

// ---- OrgSettings (from OrgSettingsController.java) ----

export interface OrgSettings {
  defaultCurrency: string;
  logoUrl?: string;
  brandColor?: string;
  documentFooterText?: string;
  // compliance fields (may be absent from API response until backend adds them)
  dormancyThresholdDays?: number;
  dataRequestDeadlineDays?: number;
  compliancePackStatus?: CompliancePackEntry[];
  // integration feature flags
  accountingEnabled?: boolean;
  aiEnabled?: boolean;
  documentSigningEnabled?: boolean;
  // tax fields
  taxRegistrationNumber?: string;
  taxRegistrationLabel?: string;
  taxLabel?: string;
  taxInclusive?: boolean;
  // acceptance fields
  acceptanceExpiryDays?: number;
}

export interface UpdateTaxSettingsRequest {
  taxRegistrationNumber?: string;
  taxRegistrationLabel?: string;
  taxLabel?: string;
  taxInclusive: boolean;
}

// ---- Tax Rates (from TaxRateController.java) ----

export interface TaxRateResponse {
  id: string;
  name: string;
  rate: number;
  isDefault: boolean;
  isExempt: boolean;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTaxRateRequest {
  name: string;
  rate: number;
  isDefault: boolean;
  isExempt: boolean;
  sortOrder: number;
}

export interface UpdateTaxRateRequest {
  name: string;
  rate: number;
  isDefault: boolean;
  isExempt: boolean;
  active: boolean;
  sortOrder: number;
}

export interface UpdateOrgSettingsRequest {
  defaultCurrency: string;
  brandColor?: string;
  documentFooterText?: string;
}

// ---- Integrations (from IntegrationController.java) ----

export type IntegrationDomain = "ACCOUNTING" | "AI" | "DOCUMENT_SIGNING" | "EMAIL" | "PAYMENT";

export interface OrgIntegration {
  domain: IntegrationDomain;
  providerSlug: string | null;
  enabled: boolean;
  keySuffix: string | null;
  configJson: string | null;
  updatedAt: string | null;
}

export interface ConnectionTestResult {
  success: boolean;
  providerName: string;
  errorMessage: string | null;
}

export interface UpsertIntegrationRequest {
  providerSlug: string;
  configJson?: string;
}

export interface SetApiKeyRequest {
  apiKey: string;
}

export interface ToggleIntegrationRequest {
  enabled: boolean;
}

// ---- Payment Provider Configs (parsed from OrgIntegration.configJson) ----

export interface StripePaymentConfig {
  webhookSigningSecret?: string;
}

export interface PayFastPaymentConfig {
  merchantId?: string;
  merchantKey?: string;
  sandbox?: boolean;
}

// ---- Document Templates (from DocumentTemplateController.java) ----

export type TemplateCategory =
  | "ENGAGEMENT_LETTER"
  | "STATEMENT_OF_WORK"
  | "COVER_LETTER"
  | "PROJECT_SUMMARY"
  | "NDA";

export type TemplateEntityType = "PROJECT" | "CUSTOMER" | "INVOICE";

export type TemplateSource = "PLATFORM" | "ORG_CUSTOM";

export interface TemplateListResponse {
  id: string;
  name: string;
  slug: string;
  description: string;
  category: TemplateCategory;
  primaryEntityType: TemplateEntityType;
  source: TemplateSource;
  sourceTemplateId: string | null;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface TemplateDetailResponse {
  id: string;
  name: string;
  slug: string;
  description: string;
  category: TemplateCategory;
  primaryEntityType: TemplateEntityType;
  content: string;
  css: string | null;
  source: TemplateSource;
  sourceTemplateId: string | null;
  packId: string | null;
  packTemplateKey: string | null;
  requiredContextFields?: Array<{ entity: string; field: string }> | null;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTemplateRequest {
  name: string;
  description?: string;
  category: TemplateCategory;
  primaryEntityType: TemplateEntityType;
  content: string;
  css?: string;
  slug?: string;
  requiredContextFields?: Array<{ entity: string; field: string }>;
}

export interface UpdateTemplateRequest {
  name: string;
  description?: string;
  content: string;
  css?: string;
  sortOrder?: number;
  requiredContextFields?: Array<{ entity: string; field: string }> | null;
}

export interface FieldValidationResult {
  entity: string;
  field: string;
  present: boolean;
  reason: string | null;
}

export interface TemplateValidationResult {
  allPresent: boolean;
  fields: FieldValidationResult[];
}

export interface PreviewResponse {
  html: string;
  validationResult?: TemplateValidationResult | null;
}

// ---- BillingRate (from BillingRateController.java) ----

export type BillingRateScope =
  | "MEMBER_DEFAULT"
  | "PROJECT_OVERRIDE"
  | "CUSTOMER_OVERRIDE";

export interface BillingRate {
  id: string;
  memberId: string;
  memberName: string;
  projectId: string | null;
  projectName: string | null;
  customerId: string | null;
  customerName: string | null;
  scope: BillingRateScope;
  currency: string;
  hourlyRate: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBillingRateRequest {
  memberId: string;
  projectId?: string;
  customerId?: string;
  currency: string;
  hourlyRate: number;
  effectiveFrom: string;
  effectiveTo?: string;
}

export interface UpdateBillingRateRequest {
  currency: string;
  hourlyRate: number;
  effectiveFrom: string;
  effectiveTo?: string;
}

export interface ResolvedRate {
  hourlyRate: number;
  currency: string;
  source: string;
  billingRateId: string;
}

// ---- CostRate (from CostRateController.java) ----

export interface CostRate {
  id: string;
  memberId: string;
  memberName: string;
  currency: string;
  hourlyCost: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCostRateRequest {
  memberId: string;
  currency: string;
  hourlyCost: number;
  effectiveFrom: string;
  effectiveTo?: string;
}

export interface UpdateCostRateRequest {
  currency: string;
  hourlyCost: number;
  effectiveFrom: string;
  effectiveTo?: string;
}

// ---- Budget (from ProjectBudgetController.java) ----

export type BudgetStatus = "ON_TRACK" | "AT_RISK" | "OVER_BUDGET";

export interface BudgetStatusResponse {
  projectId: string;
  budgetHours: number | null;
  budgetAmount: number | null;
  budgetCurrency: string | null;
  alertThresholdPct: number;
  notes: string | null;
  hoursConsumed: number;
  hoursRemaining: number;
  hoursConsumedPct: number;
  amountConsumed: number;
  amountRemaining: number;
  amountConsumedPct: number;
  hoursStatus: BudgetStatus;
  amountStatus: BudgetStatus;
  overallStatus: BudgetStatus;
}

export interface LightweightBudgetStatus {
  hoursConsumedPct: number;
  amountConsumedPct: number;
  hoursStatus: BudgetStatus | null;
  amountStatus: BudgetStatus | null;
  overallStatus: BudgetStatus | null;
}

export interface UpsertBudgetRequest {
  budgetHours?: number;
  budgetAmount?: number;
  budgetCurrency?: string;
  alertThresholdPct?: number;
  notes?: string;
}

// ---- Profitability Reports (from ReportController.java) ----

export interface MemberValueBreakdown {
  currency: string;
  billableValue: number;
  costValue: number;
}

export interface MemberUtilizationRecord {
  memberId: string;
  memberName: string;
  totalHours: number;
  billableHours: number;
  nonBillableHours: number;
  utilizationPercent: number;
  currencies: MemberValueBreakdown[];
}

export interface UtilizationResponse {
  from: string;
  to: string;
  members: MemberUtilizationRecord[];
}

export interface ProjectProfitabilitySummary {
  projectId: string;
  projectName: string;
  customerName: string | null;
  currency: string;
  billableHours: number;
  billableValue: number;
  costValue: number | null;
  margin: number | null;
  marginPercent: number | null;
}

export interface OrgProfitabilityResponse {
  projects: ProjectProfitabilitySummary[];
}

export interface CurrencyBreakdown {
  currency: string;
  totalBillableHours: number;
  totalNonBillableHours: number;
  totalHours: number;
  billableValue: number;
  costValue: number | null;
  margin: number | null;
  marginPercent: number | null;
}

export interface ProjectProfitabilityResponse {
  projectId: string;
  projectName: string;
  currencies: CurrencyBreakdown[];
}

export interface CustomerProfitabilityResponse {
  customerId: string;
  customerName: string;
  currencies: CurrencyBreakdown[];
}

// ---- Invoices (from InvoiceController.java) ----

export type InvoiceStatus = "DRAFT" | "APPROVED" | "SENT" | "PAID" | "VOID";

export interface InvoiceLineResponse {
  id: string;
  projectId: string | null;
  projectName: string | null;
  timeEntryId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  sortOrder: number;
  taxRateId: string | null;
  taxRateName: string | null;
  taxRatePercent: number | null;
  taxAmount: number | null;
  taxExempt: boolean;
}

export interface TaxBreakdownEntry {
  taxRateName: string;
  taxRatePercent: number;
  taxableAmount: number;
  taxAmount: number;
}

export interface InvoiceResponse {
  id: string;
  customerId: string;
  invoiceNumber: string | null;
  status: InvoiceStatus;
  currency: string;
  issueDate: string | null;
  dueDate: string | null;
  subtotal: number;
  taxAmount: number;
  total: number;
  notes: string | null;
  paymentTerms: string | null;
  paymentReference: string | null;
  paidAt: string | null;
  customerName: string;
  customerEmail: string | null;
  customerAddress: string | null;
  orgName: string;
  createdBy: string;
  createdByName: string | null;
  approvedBy: string | null;
  approvedByName: string | null;
  createdAt: string;
  updatedAt: string;
  lines: InvoiceLineResponse[];
  paymentSessionId: string | null;
  paymentUrl: string | null;
  paymentDestination: string | null;
  customFields?: Record<string, unknown>;
  appliedFieldGroups?: string[];
  taxBreakdown: TaxBreakdownEntry[];
  taxInclusive: boolean;
  taxRegistrationNumber: string | null;
  taxRegistrationLabel: string | null;
  taxLabel: string | null;
  hasPerLineTax: boolean;
}

export type PaymentEventStatus =
  | "CREATED"
  | "PENDING"
  | "COMPLETED"
  | "FAILED"
  | "EXPIRED"
  | "CANCELLED";

export interface PaymentEvent {
  id: string;
  providerSlug: string;
  sessionId: string | null;
  paymentReference: string | null;
  status: PaymentEventStatus;
  amount: number;
  currency: string;
  paymentDestination: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateInvoiceDraftRequest {
  customerId: string;
  currency: string;
  timeEntryIds: string[];
  dueDate?: string;
  notes?: string;
  paymentTerms?: string;
}

export interface UpdateInvoiceRequest {
  dueDate?: string;
  notes?: string;
  paymentTerms?: string;
  taxAmount?: number;
}

export interface AddLineItemRequest {
  projectId?: string;
  description: string;
  quantity: number;
  unitPrice: number;
  sortOrder?: number;
  taxRateId?: string | null;
}

export interface UpdateLineItemRequest {
  description: string;
  quantity: number;
  unitPrice: number;
  sortOrder?: number;
  taxRateId?: string | null;
}

export interface RecordPaymentRequest {
  paymentReference?: string;
}

export interface CurrencyTotal {
  hours: number;
  amount: number;
}

export interface UnbilledTimeEntry {
  id: string;
  taskTitle: string;
  memberName: string;
  date: string;
  durationMinutes: number;
  billingRateSnapshot: number;
  billingRateCurrency: string;
  billableValue: number;
  description: string | null;
}

export interface UnbilledProjectGroup {
  projectId: string;
  projectName: string;
  entries: UnbilledTimeEntry[];
  totals: Record<string, CurrencyTotal>;
}

export interface UnbilledTimeResponse {
  customerId: string;
  customerName: string;
  projects: UnbilledProjectGroup[];
  grandTotals: Record<string, CurrencyTotal>;
}

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
  visibilityCondition: {
    dependsOnSlug: string;
    operator: string;
    value: string | string[];
  } | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
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
  visibilityCondition?: {
    dependsOnSlug: string;
    operator: string;
    value: string | string[];
  } | null;
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
  visibilityCondition?: {
    dependsOnSlug: string;
    operator: string;
    value: string | string[];
  } | null;
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

// ---- Tags (from TagController.java) ----

export interface TagResponse {
  id: string;
  name: string;
  slug: string;
  color: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTagRequest {
  name: string;
  color?: string | null;
}

export interface UpdateTagRequest {
  name: string;
  color?: string | null;
}

export interface SetEntityTagsRequest {
  tagIds: string[];
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

// ---- Generated Documents (from GeneratedDocumentController.java) ----

export interface GenerateDocumentResponse {
  id: string;
  fileName: string;
  fileSize: number;
  documentId: string;
  generatedAt: string;
}

export interface GeneratedDocumentListResponse {
  id: string;
  templateName: string;
  fileName: string;
  fileSize: number;
  generatedByName: string;
  generatedAt: string;
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

// ---- Setup Status ----

export interface FieldStatus {
  name: string;
  slug: string;
  filled: boolean;
}

export interface RequiredFieldStatus {
  filled: number;
  total: number;
  fields: FieldStatus[];
}

export interface ChecklistProgress {
  checklistName: string;
  completed: number;
  total: number;
  percentComplete: number;
}

export interface ProjectSetupStatus {
  projectId: string;
  customerAssigned: boolean;
  rateCardConfigured: boolean;
  budgetConfigured: boolean;
  teamAssigned: boolean;
  requiredFields: RequiredFieldStatus;
  completionPercentage: number;
  overallComplete: boolean;
}

export interface CustomerReadiness {
  customerId: string;
  lifecycleStatus: string;
  checklistProgress: ChecklistProgress | null;
  requiredFields: RequiredFieldStatus;
  hasLinkedProjects: boolean;
  overallReadiness: string;
}

export interface ProjectUnbilledBreakdown {
  projectId: string;
  projectName: string;
  hours: number;
  amount: number;
  entryCount: number;
}

export interface UnbilledTimeSummary {
  totalHours: number;
  totalAmount: number;
  currency: string;
  entryCount: number;
  byProject: ProjectUnbilledBreakdown[] | null;
}

export interface TemplateReadiness {
  templateId: string;
  templateName: string;
  templateSlug: string;
  ready: boolean;
  missingFields: string[];
}

// ---- Retainers (shared types for client components — API functions live in @/lib/api/retainers) ----

export type RetainerType = "HOUR_BANK" | "FIXED_FEE";

export interface RetainerSummaryResponse {
  hasActiveRetainer: boolean;
  agreementId: string | null;
  agreementName: string | null;
  type: RetainerType | null;
  allocatedHours: number | null;
  consumedHours: number | null;
  remainingHours: number | null;
  percentConsumed: number | null;
  isOverage: boolean;
  periodStart: string | null;
  periodEnd: string | null;
}

// ---- Invoice Validation ----

export type ValidationSeverity = "INFO" | "WARNING" | "CRITICAL";

export interface ValidationCheck {
  name: string;
  severity: ValidationSeverity;
  passed: boolean;
  message: string;
}

// ---- Error (RFC 9457 ProblemDetail) ----

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  [key: string]: unknown;
}
