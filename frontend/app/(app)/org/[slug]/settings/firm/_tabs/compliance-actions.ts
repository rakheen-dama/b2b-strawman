"use server";

import { api, ApiError } from "@/lib/api";
import { getAuthContext } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { revalidatePath } from "next/cache";
import type {
  RetentionPolicy,
  RetentionCheckResult,
  PurgeResult,
  CreateRetentionPolicyRequest,
  UpdateRetentionPolicyRequest,
  UpdateComplianceSettingsRequest,
  UpdateDataProtectionSettingsRequest,
  CompliancePackDetail,
} from "@/lib/types";
import { matterRetentionYearsSchema, MATTER_RETENTION_OWNER_ONLY_MESSAGE } from "@/lib/schemas";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateComplianceSettings(
  slug: string,
  data: UpdateComplianceSettingsRequest
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

  revalidatePath(`/org/${slug}/settings/firm`);
  return { success: true };
}

/**
 * Firm matter retention period (Phase 88 U17, ADR-354). Rides the data-protection settings PATCH,
 * which is owner-only on the backend, and sends only `legalMatterRetentionYears` — every other
 * field is absent, so the backend keeps its stored value. The new period applies to matters
 * closed from now on; already-closed matters keep their stamped deletion date.
 */
export async function updateMatterRetentionYears(
  slug: string,
  years: number
): Promise<ActionResult> {
  const { orgSlug } = await getAuthContext();
  if (slug !== orgSlug) {
    return { success: false, error: "Organization mismatch." };
  }

  const caps = await fetchMyCapabilities();
  if (!caps.isOwner) {
    return { success: false, error: MATTER_RETENTION_OWNER_ONLY_MESSAGE };
  }

  // Same 1–30 whole-years rule as the form; the backend's 400 only says "1 field(s) have
  // validation errors", so reject here with the field's own message.
  const parsed = matterRetentionYearsSchema.safeParse(years);
  if (!parsed.success) {
    return {
      success: false,
      error: parsed.error.issues[0]?.message ?? "Retention period must be between 1 and 30 years.",
    };
  }

  const body: UpdateDataProtectionSettingsRequest = { legalMatterRetentionYears: parsed.data };
  try {
    await api.patch("/api/settings/data-protection", body);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: MATTER_RETENTION_OWNER_ONLY_MESSAGE };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/settings/firm`);
  return { success: true };
}

export async function createRetentionPolicy(
  slug: string,
  data: CreateRetentionPolicyRequest
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

  revalidatePath(`/org/${slug}/settings/firm`);
  return { success: true };
}

export async function updateRetentionPolicy(
  slug: string,
  id: string,
  data: UpdateRetentionPolicyRequest
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

  revalidatePath(`/org/${slug}/settings/firm`);
  return { success: true };
}

export async function deleteRetentionPolicy(slug: string, id: string): Promise<ActionResult> {
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

  revalidatePath(`/org/${slug}/settings/firm`);
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
  recordIds: string[]
): Promise<{ success: boolean; error?: string; result?: PurgeResult }> {
  try {
    const result = await api.post<PurgeResult>("/api/retention-policies/purge", {
      recordType,
      recordIds,
    });
    revalidatePath(`/org/${slug}/settings/firm`);
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

export async function getCompliancePackDetail(
  packId: string
): Promise<CompliancePackDetail | null> {
  try {
    return await api.get<CompliancePackDetail>(
      `/api/compliance-packs/${encodeURIComponent(packId)}`
    );
  } catch (error) {
    if (error instanceof ApiError) {
      return null;
    }
    return null;
  }
}
