"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { UpdateDataProtectionSettingsRequest } from "@/lib/types";
import type {
  RetentionPolicyExtended,
  RetentionEvaluationResult,
  RetentionExecuteResult,
  ProcessingActivity,
  CreateProcessingActivityRequest,
  PaiaGenerateResponse,
} from "@/lib/types/data-protection";

interface ActionResult<T = undefined> {
  success: boolean;
  data?: T;
  error?: string;
}

export async function updateDataProtectionSettings(
  slug: string,
  data: UpdateDataProtectionSettingsRequest,
): Promise<ActionResult> {
  try {
    await api.patch("/api/settings/data-protection", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update data protection settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/data-protection`);
  return { success: true };
}

// --- Retention Policies ---

export async function fetchRetentionPolicies(
  _slug: string,
): Promise<RetentionPolicyExtended[]> {
  try {
    return await api.get<RetentionPolicyExtended[]>(
      "/api/settings/retention-policies",
    );
  } catch (error) {
    if (error instanceof ApiError) {
      throw new Error(error.message);
    }
    throw new Error("Failed to fetch retention policies.");
  }
}

export async function updateRetentionPolicy(
  slug: string,
  id: string,
  data: {
    retentionDays?: number;
    action?: string;
    enabled?: boolean;
    description?: string;
  },
): Promise<ActionResult> {
  try {
    await api.put(`/api/settings/retention-policies/${id}`, data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update retention policies.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/data-protection`);
  return { success: true };
}

export async function evaluateRetentionPolicies(
  _slug: string,
): Promise<ActionResult<RetentionEvaluationResult>> {
  try {
    const result = await api.post<RetentionEvaluationResult>(
      "/api/settings/retention-policies/evaluate",
    );
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can evaluate retention policies.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function executeRetentionPurge(
  slug: string,
): Promise<ActionResult<RetentionExecuteResult>> {
  try {
    const result = await api.post<RetentionExecuteResult>(
      "/api/settings/retention-policies/execute",
    );
    revalidatePath(`/org/${slug}/settings/data-protection`);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can execute retention purge.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

// --- Processing Activities ---

interface PageResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

export async function fetchProcessingActivities(
  _slug: string,
): Promise<ProcessingActivity[]> {
  try {
    const response = await api.get<PageResponse<ProcessingActivity>>(
      "/api/settings/processing-activities?size=100",
    );
    return response.content;
  } catch (error) {
    if (error instanceof ApiError) {
      throw new Error(error.message);
    }
    throw new Error("Failed to fetch processing activities.");
  }
}

export async function createProcessingActivity(
  slug: string,
  data: CreateProcessingActivityRequest,
): Promise<ActionResult<ProcessingActivity>> {
  try {
    const result = await api.post<ProcessingActivity>(
      "/api/settings/processing-activities",
      data,
    );
    revalidatePath(`/org/${slug}/settings/data-protection`);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can create processing activities.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function updateProcessingActivity(
  slug: string,
  id: string,
  data: CreateProcessingActivityRequest,
): Promise<ActionResult<ProcessingActivity>> {
  try {
    const result = await api.put<ProcessingActivity>(
      `/api/settings/processing-activities/${id}`,
      data,
    );
    revalidatePath(`/org/${slug}/settings/data-protection`);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update processing activities.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function deleteProcessingActivity(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/settings/processing-activities/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can delete processing activities.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/data-protection`);
  return { success: true };
}

// --- PAIA Manual ---

export async function generatePaiaManual(
  _slug: string,
): Promise<ActionResult<PaiaGenerateResponse>> {
  try {
    const result = await api.post<PaiaGenerateResponse>(
      "/api/settings/paia-manual/generate",
    );
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can generate the PAIA manual.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
