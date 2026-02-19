"use server";

import { auth } from "@clerk/nextjs/server";
import { ApiError } from "@/lib/api";
import { transitionLifecycle, getLifecycleHistory } from "@/lib/compliance-api";
import { revalidatePath } from "next/cache";
import type { LifecycleHistoryEntry } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function transitionCustomerLifecycle(
  slug: string,
  customerId: string,
  targetStatus: string,
  notes?: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage customer lifecycle." };
  }

  try {
    await transitionLifecycle(customerId, targetStatus, notes);
  } catch (error) {
    if (error instanceof ApiError) {
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
