import "server-only";

import { api } from "./client";

// ---- Response Types ----

export interface RequestTemplateItemResponse {
  id: string;
  templateId: string;
  name: string;
  description: string | null;
  responseType: "FILE_UPLOAD" | "TEXT_RESPONSE";
  required: boolean;
  fileTypeHints: string | null;
  sortOrder: number;
  createdAt: string;
}

export interface RequestTemplateResponse {
  id: string;
  name: string;
  description: string | null;
  source: "PLATFORM" | "CUSTOM";
  packId: string | null;
  active: boolean;
  items: RequestTemplateItemResponse[];
  createdAt: string;
  updatedAt: string;
}

// ---- Request Types ----

export interface RequestTemplateItemRequest {
  name: string;
  description?: string;
  responseType: "FILE_UPLOAD" | "TEXT_RESPONSE";
  required: boolean;
  fileTypeHints?: string;
  sortOrder: number;
}

export interface CreateRequestTemplateRequest {
  name: string;
  description?: string;
  items?: RequestTemplateItemRequest[];
}

export interface UpdateRequestTemplateRequest {
  name: string;
  description?: string;
  items?: RequestTemplateItemRequest[];
}

// ---- API Functions ----

export async function listRequestTemplates(
  active?: boolean,
): Promise<RequestTemplateResponse[]> {
  const query = active !== undefined ? `?active=${active}` : "";
  return api.get<RequestTemplateResponse[]>(`/api/request-templates${query}`);
}

export async function getRequestTemplate(
  id: string,
): Promise<RequestTemplateResponse> {
  return api.get<RequestTemplateResponse>(`/api/request-templates/${id}`);
}

export async function createRequestTemplate(
  data: CreateRequestTemplateRequest,
): Promise<RequestTemplateResponse> {
  return api.post<RequestTemplateResponse>("/api/request-templates", data);
}

export async function updateRequestTemplate(
  id: string,
  data: UpdateRequestTemplateRequest,
): Promise<RequestTemplateResponse> {
  return api.put<RequestTemplateResponse>(`/api/request-templates/${id}`, data);
}

export async function deactivateRequestTemplate(id: string): Promise<void> {
  return api.delete<void>(`/api/request-templates/${id}`);
}

export async function duplicateRequestTemplate(
  id: string,
): Promise<RequestTemplateResponse> {
  return api.post<RequestTemplateResponse>(
    `/api/request-templates/${id}/duplicate`,
  );
}

// ---- Information Request Types ----

export type InformationRequestStatus =
  | "DRAFT"
  | "SENT"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export type RequestItemStatus =
  | "PENDING"
  | "SUBMITTED"
  | "ACCEPTED"
  | "REJECTED";

export interface InformationRequestItemResponse {
  id: string;
  name: string;
  description: string | null;
  responseType: "FILE_UPLOAD" | "TEXT_RESPONSE";
  required: boolean;
  fileTypeHints: string | null;
  sortOrder: number;
  status: RequestItemStatus;
  documentId: string | null;
  documentFileName: string | null;
  textResponse: string | null;
  rejectionReason: string | null;
  submittedAt: string | null;
  reviewedAt: string | null;
}

export interface InformationRequestResponse {
  id: string;
  requestNumber: string;
  customerId: string;
  customerName: string;
  projectId: string | null;
  projectName: string | null;
  portalContactId: string;
  portalContactName: string;
  portalContactEmail: string;
  status: InformationRequestStatus;
  reminderIntervalDays: number;
  sentAt: string | null;
  completedAt: string | null;
  totalItems: number;
  submittedItems: number;
  acceptedItems: number;
  rejectedItems: number;
  items: InformationRequestItemResponse[];
  createdAt: string;
}

export interface CreateInformationRequestItem {
  name: string;
  description?: string;
  responseType: "FILE_UPLOAD" | "TEXT_RESPONSE";
  required: boolean;
  fileTypeHints?: string;
}

export interface CreateInformationRequestRequest {
  requestTemplateId?: string | null;
  customerId: string;
  projectId?: string | null;
  portalContactId: string;
  reminderIntervalDays: number;
  items?: CreateInformationRequestItem[];
}

export interface InformationRequestSummary {
  totalRequests: number;
  draftCount: number;
  sentCount: number;
  inProgressCount: number;
  completedCount: number;
  cancelledCount: number;
  // Dashboard widget fields (from architecture spec)
  itemsPendingReview?: number;
  overdueRequests?: number;
  completionRateLast30Days?: number;
}

// ---- Information Request API Functions ----

export async function listRequests(filters?: {
  customerId?: string;
  projectId?: string;
  status?: InformationRequestStatus;
}): Promise<InformationRequestResponse[]> {
  const params = new URLSearchParams();
  if (filters?.customerId) params.set("customerId", filters.customerId);
  if (filters?.projectId) params.set("projectId", filters.projectId);
  if (filters?.status) params.set("status", filters.status);
  const qs = params.toString();
  return api.get<InformationRequestResponse[]>(
    `/api/information-requests${qs ? `?${qs}` : ""}`,
  );
}

export async function createRequest(
  data: CreateInformationRequestRequest,
): Promise<InformationRequestResponse> {
  return api.post<InformationRequestResponse>(
    "/api/information-requests",
    data,
  );
}

export async function getRequest(
  id: string,
): Promise<InformationRequestResponse> {
  return api.get<InformationRequestResponse>(
    `/api/information-requests/${id}`,
  );
}

export async function updateRequest(
  id: string,
  data: Partial<CreateInformationRequestRequest>,
): Promise<InformationRequestResponse> {
  return api.put<InformationRequestResponse>(
    `/api/information-requests/${id}`,
    data,
  );
}

export async function sendRequest(
  id: string,
): Promise<InformationRequestResponse> {
  return api.post<InformationRequestResponse>(
    `/api/information-requests/${id}/send`,
  );
}

export async function cancelRequest(
  id: string,
): Promise<InformationRequestResponse> {
  return api.post<InformationRequestResponse>(
    `/api/information-requests/${id}/cancel`,
  );
}

export async function addItem(
  requestId: string,
  data: CreateInformationRequestItem,
): Promise<InformationRequestResponse> {
  return api.post<InformationRequestResponse>(
    `/api/information-requests/${requestId}/items`,
    data,
  );
}

export async function acceptItem(
  requestId: string,
  itemId: string,
): Promise<InformationRequestResponse> {
  return api.post<InformationRequestResponse>(
    `/api/information-requests/${requestId}/items/${itemId}/accept`,
  );
}

export async function rejectItem(
  requestId: string,
  itemId: string,
  reason: string,
): Promise<InformationRequestResponse> {
  return api.post<InformationRequestResponse>(
    `/api/information-requests/${requestId}/items/${itemId}/reject`,
    { reason },
  );
}

export async function resendNotification(
  requestId: string,
): Promise<void> {
  return api.post<void>(
    `/api/information-requests/${requestId}/resend-notification`,
  );
}

export async function getRequestSummary(): Promise<InformationRequestSummary> {
  return api.get<InformationRequestSummary>(
    "/api/information-requests/summary",
  );
}

export async function getCustomerRequests(
  customerId: string,
): Promise<InformationRequestResponse[]> {
  return api.get<InformationRequestResponse[]>(
    `/api/customers/${customerId}/information-requests`,
  );
}

export async function getProjectRequests(
  projectId: string,
): Promise<InformationRequestResponse[]> {
  return api.get<InformationRequestResponse[]>(
    `/api/projects/${projectId}/information-requests`,
  );
}
