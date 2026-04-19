"use server";

import { revalidatePath } from "next/cache";
import { ApiError, api } from "@/lib/api";
import {
  approveDisbursement as approveDisbursementApi,
  createDisbursement as createDisbursementApi,
  listDisbursements as listDisbursementsApi,
  listUnbilled as listUnbilledApi,
  rejectDisbursement as rejectDisbursementApi,
  updateDisbursement as updateDisbursementApi,
  uploadReceipt as uploadReceiptApi,
  type CreateDisbursementRequest,
  type DisbursementResponse,
  type ListDisbursementsParams,
  type PaginatedDisbursementsResponse,
  type UnbilledDisbursementsResponse,
  type UpdateDisbursementRequest,
} from "@/lib/api/legal-disbursements";
import type { TrustTransaction } from "@/lib/types";

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

export async function approveDisbursementAction(
  slug: string,
  id: string,
  notes?: string
): Promise<ActionResult<DisbursementResponse>> {
  try {
    const result = await approveDisbursementApi(id, notes && notes !== "" ? { notes } : {});
    revalidatePath(`/org/${slug}/legal/disbursements`);
    revalidatePath(`/org/${slug}/legal/disbursements/${id}`);
    if (result?.projectId) {
      revalidatePath(`/org/${slug}/projects/${result.projectId}`);
    }
    return { success: true, data: result };
  } catch (error) {
    const message =
      error instanceof ApiError ? error.message : "Failed to approve disbursement";
    return { success: false, error: message };
  }
}

export async function rejectDisbursementAction(
  slug: string,
  id: string,
  notes: string
): Promise<ActionResult<DisbursementResponse>> {
  try {
    const result = await rejectDisbursementApi(id, { notes });
    revalidatePath(`/org/${slug}/legal/disbursements`);
    revalidatePath(`/org/${slug}/legal/disbursements/${id}`);
    if (result?.projectId) {
      revalidatePath(`/org/${slug}/projects/${result.projectId}`);
    }
    return { success: true, data: result };
  } catch (error) {
    const message =
      error instanceof ApiError ? error.message : "Failed to reject disbursement";
    return { success: false, error: message };
  }
}

/**
 * Fetches APPROVED trust transactions of type DISBURSEMENT_PAYMENT for a given project.
 *
 * Phase 60 exposes trust transactions via the account-scoped endpoint
 * `GET /api/trust-accounts/{accountId}/transactions?...`. There is no flat
 * `/api/trust-transactions?projectId=...` endpoint yet, so we walk all trust
 * accounts for the org and aggregate matching transactions. This keeps the
 * backend contract untouched (488B is frontend-only).
 */
export async function fetchApprovedTrustDisbursementPayments(
  projectId: string
): Promise<TrustTransaction[]> {
  if (!projectId) return [];
  try {
    const accounts = await api.get<{ id: string }[]>("/api/trust-accounts");
    if (!accounts || accounts.length === 0) return [];

    const transactionsPerAccount = await Promise.all(
      accounts.map(async (account) => {
        const search = new URLSearchParams();
        search.set("projectId", projectId);
        search.set("status", "APPROVED");
        search.set("type", "DISBURSEMENT_PAYMENT");
        search.set("size", "200");
        try {
          const result = await api.get<
            | { content: TrustTransaction[] }
            | TrustTransaction[]
          >(`/api/trust-accounts/${account.id}/transactions?${search.toString()}`);
          return Array.isArray(result) ? result : (result?.content ?? []);
        } catch (innerError) {
          console.error(
            `Failed to fetch trust transactions for account ${account.id}:`,
            innerError
          );
          return [];
        }
      })
    );

    return transactionsPerAccount.flat();
  } catch (error) {
    console.error("Failed to fetch approved trust disbursement payments:", error);
    return [];
  }
}

export async function fetchUnbilledDisbursementsAction(
  projectId: string
): Promise<UnbilledDisbursementsResponse | null> {
  if (!projectId) return null;
  try {
    return await listUnbilledApi({ projectId });
  } catch (error) {
    console.error("Failed to fetch unbilled disbursements:", error);
    return null;
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
