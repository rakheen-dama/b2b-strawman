"use server";

import { getAuthContext } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import { transitionLifecycle, getLifecycleHistory } from "@/lib/compliance-api";
import { revalidatePath } from "next/cache";
import type { LifecycleHistoryEntry } from "@/lib/types";
import type { PrerequisiteCheck } from "@/components/prerequisite/types";

interface ActionResult {
  success: boolean;
  error?: string;
  prerequisiteCheck?: PrerequisiteCheck;
}

export async function transitionCustomerLifecycle(
  slug: string,
  customerId: string,
  targetStatus: string,
  notes?: string,
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage customer lifecycle." };
  }

  try {
    await transitionLifecycle(customerId, targetStatus, notes);
  } catch (error) {
    if (error instanceof ApiError) {
      // 422 with prerequisite violations — return payload so caller can open PrerequisiteModal
      if (error.status === 422 && error.detail) {
        const violations = Array.isArray(error.detail.violations)
          ? error.detail.violations
          : [];
        const context =
          typeof error.detail.context === "string"
            ? error.detail.context
            : "LIFECYCLE_ACTIVATION";
        return {
          success: false,
          error: error.message,
          prerequisiteCheck: {
            passed: false,
            context,
            violations,
          },
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers`);
  revalidatePath(`/org/${slug}/customers/${customerId}`);

  return { success: true };
}

export async function fetchLifecycleHistory(customerId: string): Promise<LifecycleHistoryEntry[]> {
  try {
    return await getLifecycleHistory(customerId);
  } catch {
    return [];
  }
}
