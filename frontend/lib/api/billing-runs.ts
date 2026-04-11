import "server-only";

import { api } from "./client";

// ---- Enums / Union Types ----

export type BillingRunStatus = "PREVIEW" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";

export type BillingRunItemStatus =
  | "PENDING"
  | "GENERATING"
  | "GENERATED"
  | "FAILED"
  | "EXCLUDED"
  | "CANCELLED";

export type EntryType = "TIME_ENTRY" | "EXPENSE";

// ---- Response Interfaces ----

export interface BillingRun {
  id: string;
  name: string;
  status: BillingRunStatus;
  periodFrom: string;
  periodTo: string;
  currency: string;
  includeExpenses: boolean;
  includeRetainers: boolean;
  totalCustomers: number | null;
  totalInvoices: number | null;
  totalAmount: number | null;
  totalSent: number | null;
  totalFailed: number | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface BillingRunItem {
  id: string;
  customerId: string;
  customerName: string;
  status: BillingRunItemStatus;
  unbilledTimeAmount: number;
  unbilledExpenseAmount: number;
  unbilledTimeCount: number;
  unbilledExpenseCount: number;
  totalUnbilledAmount: number;
  hasPrerequisiteIssues: boolean;
  prerequisiteIssueReason: string | null;
  invoiceId: string | null;
  failureReason: string | null;
}

export interface BillingRunPreview {
  billingRunId: string;
  totalCustomers: number;
  totalUnbilledAmount: number;
  items: BillingRunItem[];
}

export interface BatchOperationResult {
  successCount: number;
  failureCount: number;
  failures: BatchFailure[];
}

export interface BatchFailure {
  invoiceId: string;
  reason: string;
}

export interface CustomerUnbilledSummary {
  customerId: string;
  customerName: string;
  unbilledTimeAmount: number;
  unbilledExpenseAmount: number;
  totalUnbilledAmount: number;
}

export interface RetainerPeriodPreview {
  agreementId: string;
  customerId: string;
  customerName: string;
  periodStart: string;
  periodEnd: string;
  consumedHours: number;
  estimatedAmount: number;
}

export interface UnbilledTimeEntry {
  id: string;
  taskId: string;
  memberId: string;
  date: string;
  durationMinutes: number;
  description: string | null;
  billable: boolean;
  billingRateSnapshot: number;
  billingRateCurrency: string;
  billableValue: number;
}

export interface UnbilledExpense {
  id: string;
  projectId: string;
  memberId: string;
  date: string;
  description: string | null;
  amount: number;
  currency: string;
  category: string;
  billable: boolean;
  markupPercent: number | null;
  billableAmount: number;
}

// ---- Request Interfaces ----

export interface CreateBillingRunRequest {
  name?: string;
  periodFrom: string;
  periodTo: string;
  currency: string;
  includeExpenses: boolean;
  includeRetainers: boolean;
  cutOffDate?: string;
  notes?: string;
}

export interface BatchSendRequest {
  defaultDueDate?: string;
  defaultPaymentTerms?: string;
}

export interface UpdateEntrySelectionsRequest {
  selections: EntrySelectionDto[];
}

export interface EntrySelectionDto {
  entryType: EntryType;
  entryId: string;
  included: boolean;
}

export interface RetainerGenerateRequest {
  retainerAgreementIds: string[];
}

// ---- Paginated Response ----

export interface PaginatedBillingRuns {
  content: BillingRun[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

// ---- List Params ----

export interface ListBillingRunsParams {
  status?: BillingRunStatus;
  page?: number;
  size?: number;
  sort?: string;
}

// ---- API Functions ----

export async function createBillingRun(data: CreateBillingRunRequest): Promise<BillingRun> {
  return api.post<BillingRun>("/api/billing-runs", data);
}

export async function listBillingRuns(
  params?: ListBillingRunsParams
): Promise<PaginatedBillingRuns> {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.page !== undefined) qs.set("page", String(params.page));
  if (params?.size !== undefined) qs.set("size", String(params.size));
  if (params?.sort) qs.set("sort", params.sort);
  const query = qs.toString();
  return api.get<PaginatedBillingRuns>(`/api/billing-runs${query ? `?${query}` : ""}`);
}

export async function getBillingRun(id: string): Promise<BillingRun> {
  return api.get<BillingRun>(`/api/billing-runs/${id}`);
}

export async function cancelBillingRun(id: string): Promise<void> {
  return api.delete<void>(`/api/billing-runs/${id}`);
}

export async function loadPreview(id: string, customerIds?: string[]): Promise<BillingRunPreview> {
  const body = customerIds ? { customerIds } : undefined;
  return api.post<BillingRunPreview>(`/api/billing-runs/${id}/preview`, body);
}

export async function getItems(id: string): Promise<BillingRunItem[]> {
  return api.get<BillingRunItem[]>(`/api/billing-runs/${id}/items`);
}

export async function getItem(id: string, itemId: string): Promise<BillingRunItem> {
  return api.get<BillingRunItem>(`/api/billing-runs/${id}/items/${itemId}`);
}

export async function updateSelections(
  id: string,
  itemId: string,
  selections: UpdateEntrySelectionsRequest
): Promise<BillingRunItem> {
  return api.put<BillingRunItem>(`/api/billing-runs/${id}/items/${itemId}/selections`, selections);
}

export async function excludeCustomer(id: string, itemId: string): Promise<BillingRunItem> {
  return api.put<BillingRunItem>(`/api/billing-runs/${id}/items/${itemId}/exclude`);
}

export async function includeCustomer(id: string, itemId: string): Promise<BillingRunItem> {
  return api.put<BillingRunItem>(`/api/billing-runs/${id}/items/${itemId}/include`);
}

export async function getUnbilledTime(id: string, itemId: string): Promise<UnbilledTimeEntry[]> {
  return api.get<UnbilledTimeEntry[]>(`/api/billing-runs/${id}/items/${itemId}/unbilled-time`);
}

export async function getUnbilledExpenses(id: string, itemId: string): Promise<UnbilledExpense[]> {
  return api.get<UnbilledExpense[]>(`/api/billing-runs/${id}/items/${itemId}/unbilled-expenses`);
}

export async function generate(id: string): Promise<BillingRun> {
  return api.post<BillingRun>(`/api/billing-runs/${id}/generate`);
}

export async function batchApprove(id: string): Promise<BatchOperationResult> {
  return api.post<BatchOperationResult>(`/api/billing-runs/${id}/approve`);
}

export async function batchSend(
  id: string,
  request: BatchSendRequest
): Promise<BatchOperationResult> {
  return api.post<BatchOperationResult>(`/api/billing-runs/${id}/send`, request);
}

export async function getRetainerPreview(id: string): Promise<RetainerPeriodPreview[]> {
  return api.get<RetainerPeriodPreview[]>(`/api/billing-runs/${id}/retainer-preview`);
}

export async function generateRetainers(
  id: string,
  agreementIds: string[]
): Promise<BillingRunItem[]> {
  const body: RetainerGenerateRequest = {
    retainerAgreementIds: agreementIds,
  };
  return api.post<BillingRunItem[]>(`/api/billing-runs/${id}/retainer-generate`, body);
}
