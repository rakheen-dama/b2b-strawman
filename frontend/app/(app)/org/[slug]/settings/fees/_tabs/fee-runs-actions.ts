"use server";

import { getAuthContext } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { UpdateBatchBillingSettingsRequest } from "@/lib/types";
import { paymentTermsDaysSchema } from "@/lib/schemas";

interface ActionResult {
  success: boolean;
  error?: string;
}

// The batch-billing endpoint also carries the firm-wide fee-note payment-terms default (620A contract).
export async function updateBatchBillingSettings(
  slug: string,
  data: UpdateBatchBillingSettingsRequest
): Promise<ActionResult> {
  const { orgSlug } = await getAuthContext();
  const caps = await fetchMyCapabilities();

  if (slug !== orgSlug) {
    return { success: false, error: "Organization mismatch." };
  }

  if (!caps.isAdmin && !caps.isOwner) {
    return {
      success: false,
      error: "Only admins and owners can update batch billing settings.",
    };
  }

  if (
    !Number.isFinite(data.billingBatchAsyncThreshold) ||
    data.billingBatchAsyncThreshold < 1 ||
    data.billingBatchAsyncThreshold > 1000
  ) {
    return {
      success: false,
      error: "Async threshold must be between 1 and 1000.",
    };
  }

  if (
    !Number.isFinite(data.billingEmailRateLimit) ||
    data.billingEmailRateLimit < 1 ||
    data.billingEmailRateLimit > 100
  ) {
    return {
      success: false,
      error: "Email rate limit must be between 1 and 100.",
    };
  }

  if (
    data.defaultBillingRunCurrency !== null &&
    data.defaultBillingRunCurrency !== undefined &&
    data.defaultBillingRunCurrency.length !== 3
  ) {
    return {
      success: false,
      error: "Currency must be exactly 3 characters or empty.",
    };
  }

  // Same 0–365 whole-days rule as the form (Phase 88 U3, ADR-347).
  const terms = paymentTermsDaysSchema.safeParse(data.defaultPaymentTermsDays);
  if (!terms.success) {
    return {
      success: false,
      error: terms.error.issues[0]?.message ?? "Payment terms must be between 0 and 365 days.",
    };
  }

  try {
    await api.patch("/api/settings/batch-billing", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 405) {
        return {
          success: false,
          error:
            "Batch billing settings API not available yet. This feature is coming in a future update.",
        };
      }
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update batch billing settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/settings/fees`);
  return { success: true };
}
