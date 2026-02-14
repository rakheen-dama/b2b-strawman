// ---- Projects (from ProjectController.java) ----

export interface Project {
  id: string;
  name: string;
  description: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  projectRole: ProjectRole | null;
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
}

export interface UpdateProjectRequest {
  name: string;
  description?: string;
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
  createdAt: string;
  updatedAt: string;
}

export interface CreateCustomerRequest {
  name: string;
  email: string;
  phone?: string;
  idNumber?: string;
  notes?: string;
}

export interface UpdateCustomerRequest {
  name: string;
  email: string;
  phone?: string;
  idNumber?: string;
  notes?: string;
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
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  priority?: TaskPriority;
  type?: string;
  dueDate?: string;
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
}

export interface UpdateOrgSettingsRequest {
  defaultCurrency: string;
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

// ---- Error (RFC 9457 ProblemDetail) ----

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  [key: string]: unknown;
}
