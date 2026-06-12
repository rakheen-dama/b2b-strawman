"use server";

import { getAuthContext } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { UpdateTimeTrackingSettingsRequest } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateTimeTrackingSettings(
  slug: string,
  data: UpdateTimeTrackingSettingsRequest
): Promise<ActionResult> {
  const { orgSlug } = await getAuthContext();
  const caps = await fetchMyCapabilities();

  // Validate slug matches authenticated org
  if (slug !== orgSlug) {
    return { success: false, error: "Organization mismatch." };
  }

  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can update time tracking settings." };
  }

  try {
    // Backend stores reminder threshold in minutes; the form works in hours.
    await api.patch("/api/settings/time-reminders", {
      timeReminderEnabled: data.timeReminderEnabled,
      timeReminderDays: data.timeReminderDays,
      timeReminderTime: data.timeReminderTime,
      timeReminderMinMinutes: Math.round(data.timeReminderMinHours * 60),
    });
  } catch (error) {
    if (error instanceof ApiError) {
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
