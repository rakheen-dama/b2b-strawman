import "server-only";

import { api } from "./client";

// ---- Collections Settings Types (588C) ----
// 591B extends this file with debtors/activities types — keep sections additive.

export interface CollectionsSettingsResponse {
  collectionsEnabled: boolean;
  stage1DaysOverdue: number;
  stage2DaysOverdue: number;
  stage3DaysOverdue: number;
  escalateDaysOverdue: number;
}

export interface UpdateCollectionsSettingsRequest {
  collectionsEnabled: boolean;
  stage1DaysOverdue: number;
  stage2DaysOverdue: number;
  stage3DaysOverdue: number;
  escalateDaysOverdue: number;
}

export interface CollectionsExemptionResponse {
  id: string;
  collectionsExempt: boolean;
}

// ---- Collections Settings API Functions (588C) ----

export async function getCollectionsSettings(): Promise<CollectionsSettingsResponse> {
  return api.get<CollectionsSettingsResponse>("/api/settings/collections");
}

export async function updateCollectionsSettings(
  dto: UpdateCollectionsSettingsRequest
): Promise<CollectionsSettingsResponse> {
  return api.put<CollectionsSettingsResponse>("/api/settings/collections", dto);
}

// ---- Customer Exemption API Functions (588C) ----

export async function setCollectionsExemption(
  customerId: string,
  exempt: boolean
): Promise<CollectionsExemptionResponse> {
  return api.put<CollectionsExemptionResponse>(
    `/api/customers/${customerId}/collections-exemption`,
    { collectionsExempt: exempt }
  );
}

// ---- Debtors / Activities Types (591B) ----

export interface DebtorBuckets {
  current: number;
  d30: number;
  d60: number;
  d90plus: number;
}

export interface DebtorLastActivity {
  stage: string;
  status: string;
  at: string;
}

export interface DebtorResponse {
  customerId: string;
  customerName: string;
  outstandingTotal: number;
  currency: string;
  invoiceCount: number;
  oldestDaysOverdue: number;
  buckets: DebtorBuckets;
  signals: string[];
  collectionsExempt: boolean;
  lastActivity: DebtorLastActivity | null;
}

export interface PaginatedDebtorsResponse {
  content: DebtorResponse[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

export interface OutstandingInvoice {
  invoiceId: string;
  invoiceNumber: string;
  total: number;
  currency: string;
  dueDate: string;
  daysOverdue: number;
}

export interface CollectionActivityResponse {
  id: string;
  invoiceId: string;
  stage: string;
  status: string;
  reason: string | null;
  gateId: string | null;
  daysOverdueAtAction: number;
  createdAt: string;
  updatedAt: string;
}

export interface DebtorDetailResponse {
  customerId: string;
  customerName: string;
  collectionsExempt: boolean;
  outstandingInvoices: OutstandingInvoice[];
  activities: {
    content: CollectionActivityResponse[];
    page: { totalElements: number; totalPages: number; size: number; number: number };
  };
}

// ---- Debtors / Activities API Functions (591B) ----

export async function getDebtors(params: {
  page?: number;
  size?: number;
}): Promise<PaginatedDebtorsResponse> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));
  const qs = searchParams.toString();
  return api.get<PaginatedDebtorsResponse>(`/api/collections/debtors${qs ? `?${qs}` : ""}`);
}

export async function getDebtorDetail(
  customerId: string,
  params: { page?: number; size?: number } = {}
): Promise<DebtorDetailResponse> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));
  const qs = searchParams.toString();
  return api.get<DebtorDetailResponse>(
    `/api/collections/debtors/${customerId}${qs ? `?${qs}` : ""}`
  );
}

export async function getInvoiceActivities(
  invoiceId: string
): Promise<CollectionActivityResponse[]> {
  return api.get<CollectionActivityResponse[]>(
    `/api/collections/activities?invoiceId=${invoiceId}`
  );
}
