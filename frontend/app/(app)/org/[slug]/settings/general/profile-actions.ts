"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

interface SeedingSummary {
  rateCardsTiersSeeded?: number;
  scheduleTemplatesSeeded?: number;
}

interface ActionResult {
  success: boolean;
  error?: string;
  seedingSummary?: SeedingSummary;
}

export interface ProfileSummary {
  id: string;
  name: string;
  description: string;
  modules: string[];
}

export async function updateVerticalProfile(
  slug: string,
  verticalProfile: string | null
): Promise<ActionResult> {
  try {
    const response = await api.patch<SeedingSummary | null>("/api/settings/vertical-profile", {
      verticalProfile,
    });
    revalidatePath(`/org/${slug}/settings/general`);
    return { success: true, seedingSummary: response ?? undefined };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only owners can change the vertical profile.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function fetchProfiles(): Promise<ProfileSummary[]> {
  return api.get<ProfileSummary[]>("/api/profiles");
}
