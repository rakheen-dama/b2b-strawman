"use server";

import { auth } from "@clerk/nextjs/server";
import { ApiError } from "@/lib/api";
import {
  runDormancyCheck as apiRunDormancyCheck,
  transitionLifecycle,
} from "@/lib/compliance-api";
import { revalidatePath } from "next/cache";

interface ActionResult {
  success: boolean;
  error?: string;
}

export interface DormancyCandidate {
  customerId: string;
  customerName: string;
  lastActivityDate: string | null;
  daysSinceActivity: number;
}

export async function runDormancyCheck(
  orgSlug: string,
): Promise<{ success: boolean; candidates?: DormancyCandidate[]; error?: string }> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can run dormancy checks." };
  }

  try {
    const result = await apiRunDormancyCheck();
    return { success: true, candidates: result.candidates };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to run dormancy check." };
  }
}

export async function markCustomerDormant(
  customerId: string,
  slug: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can mark customers as dormant." };
  }

  try {
    await transitionLifecycle(customerId, "DORMANT", "Marked dormant via compliance dashboard");
    revalidatePath(`/org/${slug}/compliance`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to mark customer as dormant." };
  }
}
