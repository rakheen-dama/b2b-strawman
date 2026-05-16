"use server";

import { ApiError } from "@/lib/api";
import { updateAiProfile } from "@/lib/api/ai";
import { revalidatePath } from "next/cache";
import type { UpdateAiProfileRequest } from "@/lib/api/ai";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateAiProfileAction(
  slug: string,
  data: UpdateAiProfileRequest
): Promise<ActionResult> {
  try {
    await updateAiProfile(data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to manage AI settings." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/ai`);
  return { success: true };
}
