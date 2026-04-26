"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  acceptItem,
  addItem,
  rejectItem,
  cancelRequest,
  resendNotification,
  sendRequest,
  type CreateInformationRequestItem,
  type InformationRequestResponse,
} from "@/lib/api/information-requests";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: InformationRequestResponse;
}

interface VoidActionResult {
  success: boolean;
  error?: string;
}

export async function acceptItemAction(
  slug: string,
  requestId: string,
  itemId: string
): Promise<ActionResult> {
  try {
    const data = await acceptItem(requestId, itemId);
    revalidatePath(`/org/${slug}/information-requests/${requestId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to accept item." };
  }
}

export async function rejectItemAction(
  slug: string,
  requestId: string,
  itemId: string,
  reason: string
): Promise<ActionResult> {
  try {
    const data = await rejectItem(requestId, itemId, reason);
    revalidatePath(`/org/${slug}/information-requests/${requestId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to reject item." };
  }
}

export async function cancelRequestAction(slug: string, requestId: string): Promise<ActionResult> {
  try {
    const data = await cancelRequest(requestId);
    revalidatePath(`/org/${slug}/information-requests/${requestId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to cancel request." };
  }
}

export async function addItemAction(
  slug: string,
  requestId: string,
  item: CreateInformationRequestItem
): Promise<ActionResult> {
  try {
    const data = await addItem(requestId, item);
    revalidatePath(`/org/${slug}/information-requests/${requestId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to add item." };
  }
}

export async function sendDraftAction(slug: string, requestId: string): Promise<ActionResult> {
  try {
    const data = await sendRequest(requestId);
    revalidatePath(`/org/${slug}/information-requests/${requestId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to send request." };
  }
}

export async function resendNotificationAction(requestId: string): Promise<VoidActionResult> {
  try {
    await resendNotification(requestId);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to resend notification." };
  }
}

// ---- Download (GAP-L-45) ----

interface PresignDownloadResponse {
  presignedUrl: string;
}

interface DownloadUrlResult {
  success: boolean;
  presignedUrl?: string;
  error?: string;
}

/**
 * Mint a presigned download URL for the document attached to an
 * information-request FILE_UPLOAD item. Mirrors `getDownloadUrl` in the
 * projects actions — shares the same `/api/documents/{id}/presign-download`
 * endpoint.
 */
export async function getItemDocumentDownloadUrl(documentId: string): Promise<DownloadUrlResult> {
  try {
    const result = await api.get<PresignDownloadResponse>(
      `/api/documents/${documentId}/presign-download`
    );
    return { success: true, presignedUrl: result.presignedUrl };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to get download URL." };
  }
}
