"use server";

import { ApiError } from "@/lib/api";
import { updateAiProfile } from "@/lib/api/ai";
import { revalidatePath } from "next/cache";
import { aiProfileSchema } from "@/lib/schemas/ai-profile";
import type { UpdateAiProfileRequest } from "@/lib/api/ai";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateAiProfileAction(
  slug: string,
  data: UpdateAiProfileRequest
): Promise<ActionResult> {
  // Defense-in-depth: validate server-side since server actions are publicly callable
  const parsed = aiProfileSchema.safeParse(data);
  if (!parsed.success) {
    return { success: false, error: parsed.error.issues[0]?.message ?? "Invalid input." };
  }

  try {
    await updateAiProfile(parsed.data as UpdateAiProfileRequest);
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
