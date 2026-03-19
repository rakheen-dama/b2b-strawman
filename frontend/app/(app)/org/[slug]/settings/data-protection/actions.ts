"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { UpdateDataProtectionSettingsRequest } from "@/lib/types";

interface ActionResult {
  success: boolean;
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
