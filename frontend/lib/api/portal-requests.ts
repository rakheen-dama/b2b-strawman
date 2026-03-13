/**
 * Client-side API client for portal information requests.
 * Uses portal JWT (from magic link exchange), NOT org auth.
 */

import { portalApi } from "@/lib/portal-api";

// --- Types ---

export type PortalRequestStatus =
  | "DRAFT"
  | "SENT"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export interface PortalRequestListItem {
  id: string;
  requestNumber: string;
  status: PortalRequestStatus;
  projectId: string | null;
  projectName: string | null;
  totalItems: number;
  submittedItems: number;
  acceptedItems: number;
  rejectedItems: number;
  sentAt: string | null;
  completedAt: string | null;
}

export interface PortalRequestItemDetail {
  id: string;
  name: string;
  description: string | null;
  responseType: "FILE_UPLOAD" | "TEXT_RESPONSE";
  required: boolean;
  fileTypeHints: string | null;
  sortOrder: number;
  status: "PENDING" | "SUBMITTED" | "ACCEPTED" | "REJECTED";
  rejectionReason: string | null;
  documentId: string | null;
  textResponse: string | null;
}

export interface PortalRequestDetail extends PortalRequestListItem {
  items: PortalRequestItemDetail[];
}

export interface UploadInitiationRequest {
  fileName: string;
  contentType: string;
  size: number;
}

export interface UploadInitiationResponse {
  documentId: string;
  uploadUrl: string;
  expiresAt: string;
}

export interface SubmitItemRequest {
  documentId?: string | null;
  textResponse?: string | null;
}

// --- API Functions ---

export function listPortalRequests(): Promise<PortalRequestListItem[]> {
  return portalApi.get<PortalRequestListItem[]>("/portal/requests");
}

export function getPortalRequest(id: string): Promise<PortalRequestDetail> {
  return portalApi.get<PortalRequestDetail>(`/portal/requests/${id}`);
}

export function initiateUpload(
  requestId: string,
  itemId: string,
  data: UploadInitiationRequest,
): Promise<UploadInitiationResponse> {
  return portalApi.post<UploadInitiationResponse>(
    `/portal/requests/${requestId}/items/${itemId}/upload`,
    data,
  );
}

export function submitItem(
  requestId: string,
  itemId: string,
  data: SubmitItemRequest,
): Promise<void> {
  return portalApi.post<void>(
    `/portal/requests/${requestId}/items/${itemId}/submit`,
    data,
  );
}
