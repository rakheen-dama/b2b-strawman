"use server";

import { revalidatePath } from "next/cache";
import { ApiError, api } from "@/lib/api";
import {
  createDisbursement as createDisbursementApi,
  listDisbursements as listDisbursementsApi,
  listUnbilled as listUnbilledApi,
  updateDisbursement as updateDisbursementApi,
  uploadReceipt as uploadReceiptApi,
  type CreateDisbursementRequest,
  type DisbursementResponse,
  type ListDisbursementsParams,
  type PaginatedDisbursementsResponse,
  type UnbilledDisbursementsResponse,
  type UpdateDisbursementRequest,
} from "@/lib/api/legal-disbursements";

interface ActionResult<T = DisbursementResponse> {
  success: boolean;
  error?: string;
  data?: T;
}

interface PaginatedResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

export async function fetchDisbursements(
  params: ListDisbursementsParams = {}
): Promise<PaginatedDisbursementsResponse> {
  return listDisbursementsApi(params);
}

export async function fetchUnbilledDisbursements(
  projectId: string
): Promise<UnbilledDisbursementsResponse | null> {
  try {
    return await listUnbilledApi({ projectId });
  } catch (error) {
    console.error("Failed to fetch unbilled disbursements:", error);
    return null;
  }
}

export async function createDisbursementAction(
  slug: string,
  data: CreateDisbursementRequest
): Promise<ActionResult<DisbursementResponse>> {
  try {
    const result = await createDisbursementApi(data);
    revalidatePath(`/org/${slug}/legal/disbursements`);
    revalidatePath(`/org/${slug}/projects/${data.projectId}`);
    return { success: true, data: result };
  } catch (error) {
    const message =
      error instanceof ApiError ? error.message : "Failed to create disbursement";
    return { success: false, error: message };
  }
}

export async function updateDisbursementAction(
  slug: string,
  id: string,
  data: UpdateDisbursementRequest
): Promise<ActionResult<DisbursementResponse>> {
  try {
    const result = await updateDisbursementApi(id, data);
    revalidatePath(`/org/${slug}/legal/disbursements`);
    revalidatePath(`/org/${slug}/legal/disbursements/${id}`);
    // Also revalidate the project page so the Disbursements tab reflects the update.
    if (result?.projectId) {
      revalidatePath(`/org/${slug}/projects/${result.projectId}`);
    }
    return { success: true, data: result };
  } catch (error) {
    const message =
      error instanceof ApiError ? error.message : "Failed to update disbursement";
    return { success: false, error: message };
  }
}

export async function uploadReceiptAction(
  slug: string,
  id: string,
  file: File
): Promise<ActionResult<DisbursementResponse>> {
  try {
    const result = await uploadReceiptApi(id, file);
    revalidatePath(`/org/${slug}/legal/disbursements/${id}`);
    return { success: true, data: result };
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Failed to upload receipt";
    return { success: false, error: message };
  }
}

export async function fetchProjects(): Promise<{ id: string; name: string }[]> {
  const result = await api.get<PaginatedResponse<{ id: string; name: string }>>(
    "/api/projects?size=200"
  );
  return result.content;
}

export async function fetchCustomers(): Promise<{ id: string; name: string }[]> {
  const result = await api.get<
    { id: string; name: string }[] | PaginatedResponse<{ id: string; name: string }>
  >("/api/customers?size=200");
  return Array.isArray(result) ? result : (result.content ?? []);
}
