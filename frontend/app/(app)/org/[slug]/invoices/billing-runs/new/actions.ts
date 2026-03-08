"use server";

import { getAuthContext } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  createBillingRun,
  loadPreview,
  getUnbilledTime,
  getUnbilledExpenses,
  updateSelections,
  excludeCustomer,
  includeCustomer,
  getRetainerPreview,
  generate,
  getItems,
  getBillingRun,
  batchApprove,
  batchSend,
  type BillingRun,
  type BillingRunItem,
  type BillingRunPreview,
  type CreateBillingRunRequest,
  type UnbilledTimeEntry,
  type UnbilledExpense,
  type UpdateEntrySelectionsRequest,
  type RetainerPeriodPreview,
  type BatchOperationResult,
  type BatchSendRequest,
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
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Permission denied." };
  }

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
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Permission denied." };
  }

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

interface UnbilledTimeResult extends ActionResult {
  entries?: UnbilledTimeEntry[];
}

interface UnbilledExpenseResult extends ActionResult {
  entries?: UnbilledExpense[];
}

interface UpdateSelectionsResult extends ActionResult {
  item?: BillingRunItem;
}

interface RetainerPreviewResult extends ActionResult {
  retainers?: RetainerPeriodPreview[];
}

export async function getUnbilledTimeAction(
  billingRunId: string,
  itemId: string,
): Promise<UnbilledTimeResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Permission denied." };
  }

  try {
    const entries = await getUnbilledTime(billingRunId, itemId);
    return { success: true, entries };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to load unbilled time entries." };
  }
}

export async function getUnbilledExpensesAction(
  billingRunId: string,
  itemId: string,
): Promise<UnbilledExpenseResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Permission denied." };
  }

  try {
    const entries = await getUnbilledExpenses(billingRunId, itemId);
    return { success: true, entries };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to load unbilled expenses." };
  }
}

export async function updateSelectionsAction(
  billingRunId: string,
  itemId: string,
  selections: UpdateEntrySelectionsRequest,
): Promise<UpdateSelectionsResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Permission denied." };
  }

  try {
    const item = await updateSelections(billingRunId, itemId, selections);
    return { success: true, item };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to update selections." };
  }
}

export async function excludeCustomerAction(
  billingRunId: string,
  itemId: string,
): Promise<UpdateSelectionsResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Permission denied." };
  }

  try {
    const item = await excludeCustomer(billingRunId, itemId);
    return { success: true, item };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to exclude customer." };
  }
}

export async function includeCustomerAction(
  billingRunId: string,
  itemId: string,
): Promise<UpdateSelectionsResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Permission denied." };
  }

  try {
    const item = await includeCustomer(billingRunId, itemId);
    return { success: true, item };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to include customer." };
  }
}

export async function getRetainerPreviewAction(
  billingRunId: string,
): Promise<RetainerPreviewResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Permission denied." };
  }

  try {
    const retainers = await getRetainerPreview(billingRunId);
    return { success: true, retainers };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to load retainer preview." };
  }
}

// ---- Step 4-5 Actions ----

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
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
