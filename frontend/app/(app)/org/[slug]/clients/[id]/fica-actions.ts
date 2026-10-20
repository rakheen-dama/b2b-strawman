"use server";

import { ApiError } from "@/lib/api";
import { invokeFicaVerification } from "@/lib/api/ai";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { revalidatePath } from "next/cache";
import type { FicaVerificationResponse } from "@/lib/api/ai";

interface FicaActionResult {
  success: boolean;
  error?: string;
  data?: FicaVerificationResponse;
}

export async function invokeFicaVerificationAction(
  slug: string,
  customerId: string
): Promise<FicaActionResult> {
  // Capability guard — defense in depth (backend also enforces)
  const caps = await fetchMyCapabilities();
  if (!caps.capabilities.includes("AI_EXECUTE")) {
    return { success: false, error: "You do not have permission to invoke AI skills." };
  }

  try {
    const result = await invokeFicaVerification(customerId);
    revalidatePath(`/org/${slug}/customers/${customerId}`);
    if (result.status === "FAILED") {
      return {
        success: false,
        error: "AI verification failed. Check execution history for details.",
      };
    }
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "AI budget exhausted or skill not permitted. Check AI settings.",
        };
      }
      if (error.status === 422) {
        return {
          success: false,
          error:
            error.message ||
            "Verification pre-flight check failed. Ensure documents and a pending checklist exist.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred. Please try again." };
  }
}
