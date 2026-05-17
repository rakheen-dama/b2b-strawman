"use server";

import { ApiError } from "@/lib/api";
import { approveAiGate, rejectAiGate } from "@/lib/api/ai";
import { revalidatePath } from "next/cache";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function approveGateAction(
  slug: string,
  gateId: string,
  notes?: string
): Promise<ActionResult> {
  try {
    await approveAiGate(gateId, notes);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/ai/reviews`);
  return { success: true };
}

export async function rejectGateAction(
  slug: string,
  gateId: string,
  notes?: string
): Promise<ActionResult> {
  try {
    await rejectAiGate(gateId, notes);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/ai/reviews`);
  return { success: true };
}
