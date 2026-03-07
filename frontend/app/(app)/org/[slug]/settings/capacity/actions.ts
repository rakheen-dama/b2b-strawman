"use server";

import { getAuthContext } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateCapacitySettings(
  slug: string,
  data: { defaultWeeklyCapacityHours: number },
): Promise<ActionResult> {
  const { orgSlug, orgRole } = await getAuthContext();

  if (slug !== orgSlug) {
    return { success: false, error: "Organization mismatch." };
  }

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can update capacity settings." };
  }

  try {
    await api.patch("/api/settings/capacity", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 405) {
        return {
          success: false,
          error:
            "Capacity settings API not available yet. This feature is coming in a future update.",
        };
      }
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update capacity settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/settings/capacity`);
  return { success: true };
}
