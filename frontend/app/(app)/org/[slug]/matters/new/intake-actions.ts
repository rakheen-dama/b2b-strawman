"use server";

import { ApiError } from "@/lib/api";
import { invokeMatterIntake } from "@/lib/api/ai";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import type { MatterIntakeResponse } from "@/lib/api/ai";

interface IntakeActionResult {
  success: boolean;
  error?: string;
  data?: MatterIntakeResponse;
}

export async function invokeMatterIntakeAction(
  slug: string,
  customerId: string,
  description: string
): Promise<IntakeActionResult> {
  // Capability guard — defense in depth (backend also enforces)
  const caps = await fetchMyCapabilities();
  if (!caps.capabilities.includes("AI_EXECUTE")) {
    return { success: false, error: "You do not have permission to invoke AI skills." };
  }

  try {
    const result = await invokeMatterIntake(customerId, description);
    if (result.status === "FAILED") {
      return {
        success: false,
        error: "AI intake analysis failed. Check execution history for details.",
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
            "Intake pre-flight check failed. Ensure customer is selected and description is at least 20 characters.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred. Please try again." };
  }
}
