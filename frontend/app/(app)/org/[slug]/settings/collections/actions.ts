"use server";

import { getAuthContext } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError } from "@/lib/api";
import { updateCollectionsSettings as updateCollectionsSettingsApi } from "@/lib/api/collections";
import type { UpdateCollectionsSettingsRequest } from "@/lib/api/collections";
import { revalidatePath } from "next/cache";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateCollectionsSettings(
  slug: string,
  data: UpdateCollectionsSettingsRequest
): Promise<ActionResult> {
  const { orgSlug } = await getAuthContext();
  const caps = await fetchMyCapabilities();

  // Validate slug matches authenticated org
  if (slug !== orgSlug) {
    return { success: false, error: "Organization mismatch." };
  }

  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can update collections settings." };
  }

  try {
    await updateCollectionsSettingsApi(data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update collections settings.",
        };
      }
      // A 400 ProblemDetail carries the server's strictly-increasing violation
      // message, already extracted into error.message by the fetcher.
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/settings/collections`);
  return { success: true };
}
