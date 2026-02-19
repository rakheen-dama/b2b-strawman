"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  RetentionPolicy,
  RetentionCheckResult,
  PurgeResult,
  CreateRetentionPolicyRequest,
  UpdateRetentionPolicyRequest,
  UpdateComplianceSettingsRequest,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateComplianceSettings(
  slug: string,
  data: UpdateComplianceSettingsRequest,
): Promise<ActionResult> {
  try {
    await api.patch("/api/settings/compliance", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 405) {
        return {
          success: false,
          error:
            "Compliance settings API not available yet. This feature is coming in a future update.",
        };
      }
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update compliance settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/compliance`);
  return { success: true };
}

export async function getRetentionPolicies(): Promise<RetentionPolicy[]> {
  try {
    return await api.get<RetentionPolicy[]>("/api/retention-policies");
  } catch {
    return [];
  }
}

export async function createRetentionPolicy(
  slug: string,
  data: CreateRetentionPolicyRequest,
): Promise<ActionResult> {
  try {
    await api.post<RetentionPolicy>("/api/retention-policies", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can create retention policies.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/compliance`);
  return { success: true };
}

export async function updateRetentionPolicy(
  slug: string,
  id: string,
  data: UpdateRetentionPolicyRequest,
): Promise<ActionResult> {
  try {
    await api.put<RetentionPolicy>(`/api/retention-policies/${id}`, data);
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

  revalidatePath(`/org/${slug}/settings/compliance`);
  return { success: true };
}

export async function deleteRetentionPolicy(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/retention-policies/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can delete retention policies.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/compliance`);
  return { success: true };
}

export async function runRetentionCheck(): Promise<{
  success: boolean;
  error?: string;
  result?: RetentionCheckResult;
}> {
  try {
    const result = await api.post<RetentionCheckResult>("/api/retention-policies/check");
    return { success: true, result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can run retention checks.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function executePurge(
  slug: string,
  recordType: string,
  recordIds: string[],
): Promise<{ success: boolean; error?: string; result?: PurgeResult }> {
  try {
    const result = await api.post<PurgeResult>("/api/retention-policies/purge", {
      recordType,
      recordIds,
    });
    revalidatePath(`/org/${slug}/settings/compliance`);
    return { success: true, result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can execute purges.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
