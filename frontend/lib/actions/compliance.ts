"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

interface ActionResult {
  success: boolean;
  error?: string;
}

export interface DormancyCandidate {
  id: string;
  name: string;
  lastActivityAt: string;
  daysSinceActivity: number;
  currentStatus: string;
}

interface DormancyCheckResult {
  success: boolean;
  data?: { thresholdDays: number; candidates: DormancyCandidate[] };
  error?: string;
}

export async function transitionCustomer(
  slug: string,
  customerId: string,
  targetStatus: string,
  notes?: string,
): Promise<ActionResult> {
  try {
    await api.post(`/api/customers/${customerId}/transition`, {
      targetStatus,
      ...(notes ? { notes } : {}),
    });
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

export async function checkDormancy(): Promise<DormancyCheckResult> {
  try {
    const data = await api.get<{ thresholdDays: number; candidates: DormancyCandidate[] }>(
      "/api/customers/dormancy-check",
    );
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
