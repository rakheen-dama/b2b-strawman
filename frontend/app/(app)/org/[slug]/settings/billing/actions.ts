"use server";

import { getAuthContext } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { BillingResponse, UpgradeRequest } from "@/lib/internal-api";

interface UpgradeResult {
  success: boolean;
  billing?: BillingResponse;
  error?: string;
}

export async function upgradeToPro(slug: string): Promise<UpgradeResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can upgrade plans." };
  }

  const body: UpgradeRequest = { planSlug: "pro" };

  try {
    const billing = await api.post<BillingResponse>("/api/billing/upgrade", body);
    revalidatePath(`/org/${slug}/settings/billing`);
    revalidatePath(`/org/${slug}/dashboard`);
    return { success: true, billing };
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return { success: false, error: "You don't have permission to upgrade this plan." };
    }
    return { success: false, error: "Something went wrong. Please try again later." };
  }
}
