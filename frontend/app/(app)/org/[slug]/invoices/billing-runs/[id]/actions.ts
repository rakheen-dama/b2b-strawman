"use server";

import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  cancelBillingRun,
  batchApprove,
  batchSend,
  type BatchOperationResult,
  type BatchSendRequest,
} from "@/lib/api/billing-runs";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface BatchActionResult {
  success: boolean;
  error?: string;
  data?: BatchOperationResult;
}

export async function cancelBillingRunAction(
  slug: string,
  billingRunId: string
): Promise<ActionResult> {
  try {
    await cancelBillingRun(billingRunId);
    revalidatePath(`/org/${slug}/invoices/billing-runs/${billingRunId}`);
    revalidatePath(`/org/${slug}/invoices/billing-runs`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to cancel billing run." };
  }
}

export async function batchApproveAction(
  slug: string,
  billingRunId: string
): Promise<BatchActionResult> {
  try {
    const data = await batchApprove(billingRunId);
    revalidatePath(`/org/${slug}/invoices/billing-runs/${billingRunId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to approve invoices." };
  }
}

export async function batchSendAction(
  slug: string,
  billingRunId: string,
  request: BatchSendRequest
): Promise<BatchActionResult> {
  try {
    const data = await batchSend(billingRunId, request);
    revalidatePath(`/org/${slug}/invoices/billing-runs/${billingRunId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to send invoices." };
  }
}
