"use server";

import { auth } from "@clerk/nextjs/server";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { Customer, CustomerProject, UploadInitRequest, UploadInitResponse, PresignDownloadResponse } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface UploadInitResult {
  success: boolean;
  error?: string;
  documentId?: string;
  presignedUrl?: string;
}

interface DownloadUrlResult {
  success: boolean;
  error?: string;
  presignedUrl?: string;
}

export async function initiateUpload(
  slug: string,
  projectId: string,
  fileName: string,
  contentType: string,
  size: number
): Promise<UploadInitResult> {
  const body: UploadInitRequest = { fileName, contentType, size };

  try {
    const result = await api.post<UploadInitResponse>(
      `/api/projects/${projectId}/documents/upload-init`,
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

export async function confirmUpload(
  slug: string,
  projectId: string,
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

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function cancelUpload(documentId: string): Promise<ActionResult> {
  try {
    await api.delete(`/api/documents/${documentId}/cancel`);
    return { success: true };
  } catch {
    return { success: false, error: "Failed to cancel upload." };
  }
}

export async function getDownloadUrl(documentId: string): Promise<DownloadUrlResult> {
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

// ---- Customer-project linking actions ----

export async function fetchCustomers(): Promise<Customer[]> {
  return api.get<Customer[]>("/api/customers");
}

export async function linkCustomerToProject(
  slug: string,
  projectId: string,
  customerId: string
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can link customers." };
  }

  try {
    await api.post<CustomerProject>(`/api/projects/${projectId}/customers/${customerId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}

export async function unlinkCustomerFromProject(
  slug: string,
  projectId: string,
  customerId: string
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can unlink customers." };
  }

  try {
    await api.delete(`/api/projects/${projectId}/customers/${customerId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}
