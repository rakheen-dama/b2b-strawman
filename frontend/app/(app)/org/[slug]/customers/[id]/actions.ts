"use server";

import { auth } from "@clerk/nextjs/server";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  Project,
  CustomerProject,
  Document,
  UploadInitRequest,
  UploadInitResponse,
  DocumentVisibility,
} from "@/lib/types";

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

export async function fetchCustomerProjects(customerId: string): Promise<Project[]> {
  return api.get<Project[]>(`/api/customers/${customerId}/projects`);
}

export async function fetchProjects(): Promise<Project[]> {
  return api.get<Project[]>("/api/projects");
}

export async function linkProject(
  slug: string,
  customerId: string,
  projectId: string
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can link projects." };
  }

  try {
    await api.post<CustomerProject>(`/api/customers/${customerId}/projects/${projectId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);

  return { success: true };
}

export async function unlinkProject(
  slug: string,
  customerId: string,
  projectId: string
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can unlink projects." };
  }

  try {
    await api.delete(`/api/customers/${customerId}/projects/${projectId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);

  return { success: true };
}

// ---- Customer document actions ----

export async function fetchCustomerDocuments(customerId: string): Promise<Document[]> {
  return api.get<Document[]>(`/api/documents?scope=CUSTOMER&customerId=${customerId}`);
}

export async function initiateCustomerUpload(
  slug: string,
  customerId: string,
  fileName: string,
  contentType: string,
  size: number
): Promise<UploadInitResult> {
  const body: UploadInitRequest = { fileName, contentType, size };

  try {
    const result = await api.post<UploadInitResponse>(
      `/api/customers/${customerId}/documents/upload-init`,
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

export async function confirmCustomerUpload(
  slug: string,
  customerId: string,
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

  revalidatePath(`/org/${slug}/customers/${customerId}`);

  return { success: true };
}

export async function cancelCustomerUpload(documentId: string): Promise<ActionResult> {
  try {
    await api.delete(`/api/documents/${documentId}/cancel`);
    return { success: true };
  } catch {
    return { success: false, error: "Failed to cancel upload." };
  }
}

export async function toggleDocumentVisibility(
  slug: string,
  customerId: string,
  documentId: string,
  visibility: DocumentVisibility
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can toggle visibility." };
  }

  try {
    await api.patch(`/api/documents/${documentId}/visibility`, { visibility });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to update visibility." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);

  return { success: true };
}
