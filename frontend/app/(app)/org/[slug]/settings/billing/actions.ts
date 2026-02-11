"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { BillingResponse, UpgradeRequest } from "@/lib/internal-api";

interface UpgradeResult {
  success: boolean;
  billing?: BillingResponse;
  error?: string;
}

export async function upgradeToPro(slug: string): Promise<UpgradeResult> {
  const body: UpgradeRequest = { planSlug: "pro" };

  try {
    const billing = await api.post<BillingResponse>("/api/billing/upgrade", body);
    revalidatePath(`/org/${slug}/settings/billing`);
    return { success: true, billing };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
