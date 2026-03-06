"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateRequestReminderSettings(
  slug: string,
  defaultRequestReminderDays: number,
): Promise<ActionResult> {
  try {
    await api.patch("/api/settings/request-reminders", {
      defaultRequestReminderDays,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 405) {
        return {
          success: false,
          error:
            "Request reminder settings API not available yet. This feature is coming in a future update.",
        };
      }
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update request settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/request-settings`);
  return { success: true };
}
