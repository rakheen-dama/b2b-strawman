"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError } from "@/lib/api";
import {
  generate,
  getItems,
  getBillingRun,
  batchApprove,
  batchSend,
  type BillingRun,
  type BillingRunItem,
  type BatchOperationResult,
  type BatchSendRequest,
} from "@/lib/api/billing-runs";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface GenerateResult extends ActionResult {
  billingRun?: BillingRun;
}

interface GetItemsResult extends ActionResult {
  items?: BillingRunItem[];
}

interface BatchOperationActionResult extends ActionResult {
  result?: BatchOperationResult;
}

interface GetBillingRunResult extends ActionResult {
  billingRun?: BillingRun;
}

export async function generateAction(
  billingRunId: string,
): Promise<GenerateResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Permission denied." };
  }
  try {
    const billingRun = await generate(billingRunId);
    return { success: true, billingRun };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to generate invoices." };
  }
}

export async function getItemsAction(
  billingRunId: string,
): Promise<GetItemsResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Permission denied." };
  }
  try {
    const items = await getItems(billingRunId);
    return { success: true, items };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to load items." };
  }
}

export async function getBillingRunAction(
  billingRunId: string,
): Promise<GetBillingRunResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Permission denied." };
  }
  try {
    const billingRun = await getBillingRun(billingRunId);
    return { success: true, billingRun };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to load billing run." };
  }
}

export async function batchApproveAction(
  billingRunId: string,
): Promise<BatchOperationActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Permission denied." };
  }
  try {
    const result = await batchApprove(billingRunId);
    return { success: true, result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to approve invoices." };
  }
}

export async function batchSendAction(
  billingRunId: string,
  request: BatchSendRequest,
): Promise<BatchOperationActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Permission denied." };
  }
  try {
    const result = await batchSend(billingRunId, request);
    return { success: true, result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to send invoices." };
  }
}
