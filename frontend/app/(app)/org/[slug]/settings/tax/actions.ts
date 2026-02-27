"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { UpdateTaxSettingsRequest } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateTaxSettings(
  slug: string,
  data: UpdateTaxSettingsRequest,
): Promise<ActionResult> {
  try {
    await api.patch("/api/settings/tax", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 405) {
        return { success: false, error: "Tax settings API not available yet." };
      }
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update tax settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/tax`);
  return { success: true };
}
