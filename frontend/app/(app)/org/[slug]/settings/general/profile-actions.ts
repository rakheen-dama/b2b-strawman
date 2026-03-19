"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

interface ActionResult {
  success: boolean;
  error?: string;
}

export interface ProfileSummary {
  id: string;
  name: string;
  description: string;
  modules: string[];
}

export async function updateVerticalProfile(
  slug: string,
  verticalProfile: string | null,
): Promise<ActionResult> {
  try {
    await api.patch("/api/settings/vertical-profile", { verticalProfile });
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
  revalidatePath(`/org/${slug}/settings/general`);
  return { success: true };
}

export async function fetchProfiles(): Promise<ProfileSummary[]> {
  return api.get<ProfileSummary[]>("/api/profiles");
}
