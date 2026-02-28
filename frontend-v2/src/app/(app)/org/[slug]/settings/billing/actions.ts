"use server";

import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";

interface UpgradeResult {
  success: boolean;
  error?: string;
}

export async function upgradeToPro(): Promise<UpgradeResult> {
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can upgrade." };
  }

  try {
    await api.post("/api/billing/upgrade", { tier: "PRO" });
    return { success: true };
  } catch (err: unknown) {
    const message =
      err instanceof Error ? err.message : "Failed to upgrade plan.";
    return { success: false, error: message };
  }
}
