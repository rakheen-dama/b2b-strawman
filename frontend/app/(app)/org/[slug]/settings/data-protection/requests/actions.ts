"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { DsarRequest } from "@/lib/types/data-protection";

interface ActionResult<T = undefined> {
  success: boolean;
  data?: T;
  error?: string;
}

export interface CreateDsarRequestData {
  customerId: string;
  requestType: string;
  description: string;
}

export interface UpdateDsarStatusData {
  action: "START_PROCESSING" | "COMPLETE" | "REJECT";
  reason?: string;
}

export async function fetchDsarRequests(
  _slug: string,
): Promise<DsarRequest[]> {
  try {
    return await api.get<DsarRequest[]>("/api/data-requests");
  } catch (error) {
    if (error instanceof ApiError) {
      throw new Error(error.message);
    }
    throw new Error("Failed to fetch DSAR requests.");
  }
}

export async function createDsarRequest(
  slug: string,
  data: CreateDsarRequestData,
): Promise<ActionResult<DsarRequest>> {
  try {
    const result = await api.post<DsarRequest>("/api/data-requests", data);
    revalidatePath(`/org/${slug}/settings/data-protection/requests`);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can log DSAR requests.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function updateDsarStatus(
  slug: string,
  id: string,
  action: "START_PROCESSING" | "COMPLETE" | "REJECT",
  reason?: string,
): Promise<ActionResult<DsarRequest>> {
  try {
    const result = await api.put<DsarRequest>(
      `/api/data-requests/${id}/status`,
      {
        action,
        reason: reason ?? null,
      },
    );
    revalidatePath(`/org/${slug}/settings/data-protection/requests`);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update DSAR status.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
