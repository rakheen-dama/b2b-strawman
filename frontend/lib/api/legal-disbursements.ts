import "server-only";

import { redirect } from "next/navigation";
import { api, API_BASE, ApiError, getAuthFetchOptions } from "./client";

// ---- Enums ----

export type DisbursementCategory =
  | "SHERIFF_FEES"
  | "COUNSEL_FEES"
  | "SEARCH_FEES"
  | "DEEDS_OFFICE_FEES"
  | "COURT_FEES"
  | "ADVOCATE_FEES"
  | "EXPERT_WITNESS"
  | "TRAVEL"
  | "OTHER";

export type VatTreatment = "STANDARD_15" | "ZERO_RATED_PASS_THROUGH" | "EXEMPT";

export type DisbursementPaymentSource = "OFFICE_ACCOUNT" | "TRUST_ACCOUNT";

export type DisbursementApprovalStatus = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";

export type DisbursementBillingStatus = "UNBILLED" | "BILLED" | "WRITTEN_OFF";

// ---- Response types ----

export interface DisbursementResponse {
  id: string;
  projectId: string;
  customerId: string;
  category: DisbursementCategory;
  description: string;
  amount: number;
  vatTreatment: VatTreatment;
  vatAmount: number;
  paymentSource: DisbursementPaymentSource;
  trustTransactionId: string | null;
  incurredDate: string; // ISO yyyy-MM-dd
  supplierName: string;
  supplierReference: string | null;
  receiptDocumentId: string | null;
  approvalStatus: DisbursementApprovalStatus;
  approvedBy: string | null;
  approvedAt: string | null;
  approvalNotes: string | null;
  billingStatus: DisbursementBillingStatus;
  invoiceLineId: string | null;
  writeOffReason: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface UnbilledDisbursementItem {
  id: string;
  incurredDate: string;
  category: DisbursementCategory;
  description: string;
  amount: number;
  vatTreatment: VatTreatment;
  vatAmount: number;
  supplierName: string;
}

export interface UnbilledDisbursementsResponse {
  projectId: string;
  currency: "ZAR";
  items: UnbilledDisbursementItem[];
  totalAmount: number;
  totalVat: number;
}

export interface PaginatedDisbursementsResponse {
  content: DisbursementResponse[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

// ---- Request types ----

export interface CreateDisbursementRequest {
  projectId: string;
  customerId: string;
  category: DisbursementCategory;
  description: string;
  amount: number;
  vatTreatment?: VatTreatment | null;
  paymentSource: DisbursementPaymentSource;
  trustTransactionId?: string | null;
  incurredDate: string;
  supplierName: string;
  supplierReference?: string | null;
  receiptDocumentId?: string | null;
}

export interface UpdateDisbursementRequest {
  category?: DisbursementCategory | null;
  description?: string | null;
  amount?: number | null;
  vatTreatment?: VatTreatment | null;
  incurredDate?: string | null;
  supplierName?: string | null;
  supplierReference?: string | null;
  receiptDocumentId?: string | null;
}

export interface ApprovalDecisionRequest {
  notes?: string;
}

export interface WriteOffRequest {
  reason: string;
}

// ---- List params ----

export interface ListDisbursementsParams {
  projectId?: string;
  approvalStatus?: DisbursementApprovalStatus;
  billingStatus?: DisbursementBillingStatus;
  category?: DisbursementCategory;
  page?: number;
  size?: number;
  sort?: string;
}

// ---- API functions ----

export async function listDisbursements(
  params: ListDisbursementsParams = {}
): Promise<PaginatedDisbursementsResponse> {
  const search = new URLSearchParams();
  if (params.projectId) search.set("projectId", params.projectId);
  if (params.approvalStatus) search.set("approvalStatus", params.approvalStatus);
  if (params.billingStatus) search.set("billingStatus", params.billingStatus);
  if (params.category) search.set("category", params.category);
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 50));
  if (params.sort) search.set("sort", params.sort);

  return api.get<PaginatedDisbursementsResponse>(`/api/legal/disbursements?${search.toString()}`);
}

export async function getDisbursement(id: string): Promise<DisbursementResponse> {
  return api.get<DisbursementResponse>(`/api/legal/disbursements/${id}`);
}

export async function createDisbursement(
  data: CreateDisbursementRequest
): Promise<DisbursementResponse> {
  return api.post<DisbursementResponse>("/api/legal/disbursements", data);
}

export async function updateDisbursement(
  id: string,
  data: UpdateDisbursementRequest
): Promise<DisbursementResponse> {
  return api.patch<DisbursementResponse>(`/api/legal/disbursements/${id}`, data);
}

export async function submitForApproval(id: string): Promise<DisbursementResponse> {
  return api.post<DisbursementResponse>(`/api/legal/disbursements/${id}/submit`);
}

export async function approveDisbursement(
  id: string,
  data: ApprovalDecisionRequest = {}
): Promise<DisbursementResponse> {
  return api.post<DisbursementResponse>(`/api/legal/disbursements/${id}/approve`, data);
}

export async function rejectDisbursement(
  id: string,
  data: ApprovalDecisionRequest = {}
): Promise<DisbursementResponse> {
  return api.post<DisbursementResponse>(`/api/legal/disbursements/${id}/reject`, data);
}

export async function writeOffDisbursement(
  id: string,
  data: WriteOffRequest
): Promise<DisbursementResponse> {
  return api.post<DisbursementResponse>(`/api/legal/disbursements/${id}/write-off`, data);
}

export async function listUnbilled(params: {
  projectId: string;
}): Promise<UnbilledDisbursementsResponse> {
  const search = new URLSearchParams();
  search.set("projectId", params.projectId);
  return api.get<UnbilledDisbursementsResponse>(
    `/api/legal/disbursements/unbilled?${search.toString()}`
  );
}

export async function uploadReceipt(id: string, file: File): Promise<DisbursementResponse> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("POST");
  } catch {
    redirect("/sign-in");
  }

  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE}/api/legal/disbursements/${id}/receipt`, {
    method: "POST",
    headers: { ...authOptions.headers },
    body: formData,
    credentials: authOptions.credentials,
  });

  if (!response.ok) {
    let message = response.statusText;
    try {
      const detail = await response.json();
      message = detail?.detail || detail?.title || message;
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message);
  }

  return response.json() as Promise<DisbursementResponse>;
}
