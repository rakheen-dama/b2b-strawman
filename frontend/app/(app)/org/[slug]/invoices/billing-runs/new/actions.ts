"use server";

import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  createBillingRun,
  loadPreview,
  type BillingRun,
  type BillingRunPreview,
  type CreateBillingRunRequest,
} from "@/lib/api/billing-runs";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface CreateBillingRunResult extends ActionResult {
  billingRun?: BillingRun;
}

interface LoadPreviewResult extends ActionResult {
  preview?: BillingRunPreview;
}

export async function createBillingRunAction(
  slug: string,
  data: CreateBillingRunRequest,
): Promise<CreateBillingRunResult> {
  try {
    const billingRun = await createBillingRun(data);
    revalidatePath(`/org/${slug}/invoices/billing-runs`);
    return { success: true, billingRun };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to create billing run." };
  }
}

export async function loadPreviewAction(
  slug: string,
  billingRunId: string,
): Promise<LoadPreviewResult> {
  try {
    const preview = await loadPreview(billingRunId);
    return { success: true, preview };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to load preview." };
  }
}

export async function getUnbilledSummaryAction(): Promise<ActionResult> {
  // Placeholder for future use
  return { success: true };
}
