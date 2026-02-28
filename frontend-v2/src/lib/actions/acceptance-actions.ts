"use server";

import { api, ApiError } from "@/lib/api";

// ---- Types ----

export type AcceptanceStatus =
  | "PENDING"
  | "SENT"
  | "VIEWED"
  | "ACCEPTED"
  | "EXPIRED"
  | "REVOKED";

export interface ActionResult<T = void> {
  success: boolean;
  data?: T;
  error?: string;
}

export interface PortalContactSummary {
  id: string;
  displayName: string;
  email: string;
}

export interface AcceptanceRequestContact {
  id: string;
  displayName: string;
  email: string;
}

export interface AcceptanceRequestDocument {
  id: string;
  fileName: string;
}

export interface AcceptanceRequestResponse {
  id: string;
  generatedDocumentId: string;
  portalContactId: string;
  customerId: string;
  status: AcceptanceStatus;
  sentAt: string | null;
  viewedAt: string | null;
  acceptedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
  acceptorName: string | null;
  hasCertificate: boolean;
  certificateFileName: string | null;
  sentByMemberId: string;
  revokedByMemberId: string | null;
  reminderCount: number;
  lastRemindedAt: string | null;
  createdAt: string;
  updatedAt: string;
  contact: AcceptanceRequestContact;
  document: AcceptanceRequestDocument;
}

// ---- Server Actions ----

export async function sendForAcceptance(
  generatedDocumentId: string,
  portalContactId: string,
  expiryDays?: number,
): Promise<ActionResult<AcceptanceRequestResponse>> {
  try {
    const data = await api.post<AcceptanceRequestResponse>(
      "/api/acceptance-requests",
      {
        generatedDocumentId,
        portalContactId,
        ...(expiryDays != null ? { expiryDays } : {}),
      },
    );
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to send acceptance requests.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function getAcceptanceRequests(opts: {
  documentId?: string;
  customerId?: string;
}): Promise<AcceptanceRequestResponse[]> {
  try {
    const params = new URLSearchParams();
    if (opts.documentId) params.set("documentId", opts.documentId);
    if (opts.customerId) params.set("customerId", opts.customerId);
    const query = params.toString();
    return await api.get<AcceptanceRequestResponse[]>(
      `/api/acceptance-requests${query ? `?${query}` : ""}`,
    );
  } catch (error) {
    console.warn("Failed to fetch acceptance requests (returning empty list):", error);
    return [];
  }
}

export async function remindAcceptance(
  id: string,
): Promise<ActionResult<AcceptanceRequestResponse>> {
  try {
    const data = await api.post<AcceptanceRequestResponse>(
      `/api/acceptance-requests/${id}/remind`,
    );
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to send reminders.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function revokeAcceptance(
  id: string,
): Promise<ActionResult<AcceptanceRequestResponse>> {
  try {
    const data = await api.post<AcceptanceRequestResponse>(
      `/api/acceptance-requests/${id}/revoke`,
    );
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to revoke acceptance requests.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function getAcceptanceDetail(
  id: string,
): Promise<AcceptanceRequestResponse | null> {
  try {
    return await api.get<AcceptanceRequestResponse>(
      `/api/acceptance-requests/${id}`,
    );
  } catch (error) {
    console.error("Failed to fetch acceptance detail:", error);
    return null;
  }
}

export async function downloadCertificate(
  id: string,
): Promise<{ success: boolean; pdfBase64?: string; error?: string }> {
  try {
    const { downloadCertificateBlob } = await import("@/lib/api");
    const blob = await downloadCertificateBlob(id);
    const arrayBuffer = await blob.arrayBuffer();
    const base64 = Buffer.from(arrayBuffer).toString("base64");
    return { success: true, pdfBase64: base64 };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to download certificate." };
  }
}

export async function fetchPortalContacts(
  customerId: string,
): Promise<PortalContactSummary[]> {
  try {
    const contacts = await api.get<PortalContactSummary[]>(
      `/api/customers/${customerId}/portal-contacts`,
    );
    return contacts;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return [];
    }
    console.error("Failed to fetch portal contacts:", error);
    return [];
  }
}
