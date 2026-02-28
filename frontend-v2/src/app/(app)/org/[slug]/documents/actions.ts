"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  UploadInitRequest,
  UploadInitResponse,
  DocumentVisibility,
} from "@/lib/types";

interface UploadInitResult {
  success: boolean;
  error?: string;
  documentId?: string;
  presignedUrl?: string;
}

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function initiateOrgUpload(
  slug: string,
  fileName: string,
  contentType: string,
  size: number
): Promise<UploadInitResult> {
  const body: UploadInitRequest = { fileName, contentType, size };

  try {
    const result = await api.post<UploadInitResponse>(
      "/api/documents/upload-init",
      body
    );
    return {
      success: true,
      documentId: result.documentId,
      presignedUrl: result.presignedUrl,
    };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to initiate upload." };
  }
}

export async function confirmOrgUpload(
  slug: string,
  documentId: string
): Promise<ActionResult> {
  try {
    await api.post(`/api/documents/${documentId}/confirm`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to confirm upload." };
  }

  revalidatePath(`/org/${slug}/documents`);

  return { success: true };
}

export async function cancelOrgUpload(
  documentId: string
): Promise<ActionResult> {
  try {
    await api.delete(`/api/documents/${documentId}/cancel`);
    return { success: true };
  } catch {
    return { success: false, error: "Failed to cancel upload." };
  }
}

export async function toggleDocumentVisibility(
  slug: string,
  documentId: string,
  visibility: DocumentVisibility
): Promise<ActionResult> {
  try {
    await api.patch(`/api/documents/${documentId}/visibility`, { visibility });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to update visibility." };
  }

  revalidatePath(`/org/${slug}/documents`);

  return { success: true };
}
