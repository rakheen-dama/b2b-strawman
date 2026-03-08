"use server";

import { getAuthContext } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { OrgSettings } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateProjectNamingPattern(
  slug: string,
  projectNamingPattern: string | null,
): Promise<ActionResult> {
  const { orgSlug, orgRole } = await getAuthContext();

  if (slug !== orgSlug) {
    return { success: false, error: "Organization mismatch." };
  }

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can update project naming settings." };
  }

  try {
    // Fetch current settings to preserve existing values
    const current = await api.get<OrgSettings>("/api/settings");
    await api.put<OrgSettings>("/api/settings", {
      defaultCurrency: current.defaultCurrency,
      brandColor: current.brandColor ?? null,
      documentFooterText: current.documentFooterText ?? null,
      accountingEnabled: current.accountingEnabled ?? false,
      aiEnabled: current.aiEnabled ?? false,
      documentSigningEnabled: current.documentSigningEnabled ?? false,
      projectNamingPattern: projectNamingPattern || null,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update project naming settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/settings/project-naming`);
  return { success: true };
}
