"use server";

import { ApiError } from "@/lib/api";
import { batchApproveAiGates, rejectAiGate } from "@/lib/api/ai";
import type { GateDisposition } from "@/lib/api/ai";
import { revalidatePath } from "next/cache";

export interface BatchApproveActionResult {
  success: boolean;
  error?: string;
  results?: GateDisposition[];
}

export interface ActionResult {
  success: boolean;
  error?: string;
}

/**
 * Batch-approve the selected SEND_COLLECTION_REMINDER gates. The backend runs each
 * gate in its own transaction and returns a per-gate disposition (APPROVED_EXECUTED /
 * FAILED) — partial failure is the normal case (e.g. a gate expired because the
 * invoice was paid in the meantime), so callers render the dispositions rather than
 * treating the whole batch as failed.
 */
export async function batchApproveGatesAction(
  slug: string,
  gateIds: string[],
  notes?: string
): Promise<BatchApproveActionResult> {
  try {
    const response = await batchApproveAiGates(gateIds, notes);
    revalidatePath(`/org/${slug}/invoices/collections`);
    return { success: true, results: response.results };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

/**
 * Single-gate reject — kept per-card (mirrors the ai/reviews reject flow) so an
 * approver can decline one drafted reminder without touching the rest of the queue.
 */
export async function rejectReminderGateAction(
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
  revalidatePath(`/org/${slug}/invoices/collections`);
  return { success: true };
}
