import "server-only";

import { api } from "@/lib/api";
import type {
  InvoiceResponse,
  InvoiceStatus,
  CreateInvoiceDraftRequest,
  UpdateInvoiceRequest,
  AddLineItemRequest,
  UpdateLineItemRequest,
  InvoiceLineResponse,
  RecordPaymentRequest,
  PaymentEvent,
  UnbilledProjectGroup,
  ValidationCheck,
} from "@/lib/types";

// ---- Paginated Response ----

export interface PaginatedInvoices {
  content: InvoiceResponse[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

// ---- List Params ----

export interface ListInvoicesParams {
  status?: InvoiceStatus;
  customerId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

// ---- API Functions ----

export async function fetchInvoices(
  params?: ListInvoicesParams,
): Promise<PaginatedInvoices> {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.customerId) qs.set("customerId", params.customerId);
  if (params?.page !== undefined) qs.set("page", String(params.page));
  if (params?.size !== undefined) qs.set("size", String(params.size));
  if (params?.sort) qs.set("sort", params.sort);
  const query = qs.toString();
  return api.get<PaginatedInvoices>(`/api/invoices${query ? `?${query}` : ""}`);
}

export async function fetchInvoice(id: string): Promise<InvoiceResponse> {
  return api.get<InvoiceResponse>(`/api/invoices/${id}`);
}

export async function createInvoiceDraft(
  data: CreateInvoiceDraftRequest,
): Promise<InvoiceResponse> {
  return api.post<InvoiceResponse>("/api/invoices", data);
}

export async function updateInvoice(
  id: string,
  data: UpdateInvoiceRequest,
): Promise<InvoiceResponse> {
  return api.put<InvoiceResponse>(`/api/invoices/${id}`, data);
}

export async function approveInvoice(id: string): Promise<InvoiceResponse> {
  return api.post<InvoiceResponse>(`/api/invoices/${id}/approve`);
}

export async function sendInvoice(id: string): Promise<InvoiceResponse> {
  return api.post<InvoiceResponse>(`/api/invoices/${id}/send`);
}

export async function voidInvoice(id: string): Promise<InvoiceResponse> {
  return api.post<InvoiceResponse>(`/api/invoices/${id}/void`);
}

export async function recordPayment(
  id: string,
  data?: RecordPaymentRequest,
): Promise<InvoiceResponse> {
  return api.post<InvoiceResponse>(`/api/invoices/${id}/pay`, data ?? {});
}

// ---- Line Items ----

export async function addLineItem(
  invoiceId: string,
  data: AddLineItemRequest,
): Promise<InvoiceLineResponse> {
  return api.post<InvoiceLineResponse>(
    `/api/invoices/${invoiceId}/lines`,
    data,
  );
}

export async function updateLineItem(
  invoiceId: string,
  lineId: string,
  data: UpdateLineItemRequest,
): Promise<InvoiceLineResponse> {
  return api.put<InvoiceLineResponse>(
    `/api/invoices/${invoiceId}/lines/${lineId}`,
    data,
  );
}

export async function deleteLineItem(
  invoiceId: string,
  lineId: string,
): Promise<void> {
  return api.delete<void>(`/api/invoices/${invoiceId}/lines/${lineId}`);
}

// ---- Payment Events ----

export async function fetchPaymentEvents(
  invoiceId: string,
): Promise<PaymentEvent[]> {
  return api.get<PaymentEvent[]>(`/api/invoices/${invoiceId}/payments`);
}

// ---- Unbilled Time ----

export async function fetchUnbilledTime(
  customerId: string,
): Promise<UnbilledProjectGroup[]> {
  return api.get<UnbilledProjectGroup[]>(
    `/api/customers/${customerId}/unbilled-time`,
  );
}

// ---- Validation ----

export async function validateInvoice(
  invoiceId: string,
): Promise<ValidationCheck[]> {
  return api.get<ValidationCheck[]>(`/api/invoices/${invoiceId}/validate`);
}
