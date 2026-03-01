"use server";

import { getAuthContext } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { UpdateTimeTrackingSettingsRequest } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateTimeTrackingSettings(
  slug: string,
  data: UpdateTimeTrackingSettingsRequest,
): Promise<ActionResult> {
  const { orgSlug, orgRole } = await getAuthContext();

  // Validate slug matches authenticated org
  if (slug !== orgSlug) {
    return { success: false, error: "Organization mismatch." };
  }

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can update time tracking settings." };
  }

  try {
    await api.put("/api/settings", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 405) {
        return {
          success: false,
          error:
            "Time tracking settings API not available yet. This feature is coming in a future update.",
        };
      }
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update time tracking settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/settings/time-tracking`);
  return { success: true };
}
