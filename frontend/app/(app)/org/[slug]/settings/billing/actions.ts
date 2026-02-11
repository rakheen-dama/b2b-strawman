"use server";

import { auth } from "@clerk/nextjs/server";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { BillingResponse, UpgradeRequest } from "@/lib/internal-api";

interface UpgradeResult {
  success: boolean;
  billing?: BillingResponse;
  error?: string;
}

export async function upgradeToPro(slug: string): Promise<UpgradeResult> {
  const { orgRole } = await auth();
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
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
