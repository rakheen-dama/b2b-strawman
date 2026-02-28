"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CreateExpenseRequest,
  UpdateExpenseRequest,
  ExpenseResponse,
  ExpenseCategory,
  PaginatedExpenseResponse,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function createExpense(
  slug: string,
  projectId: string,
  formData: FormData,
): Promise<ActionResult> {
  const date = formData.get("date")?.toString().trim() ?? "";
  if (!date) {
    return { success: false, error: "Date is required." };
  }

  const description = formData.get("description")?.toString().trim() ?? "";
  if (!description) {
    return { success: false, error: "Description is required." };
  }

  const amountStr = formData.get("amount")?.toString().trim() ?? "";
  const amount = parseFloat(amountStr);
  if (isNaN(amount) || amount <= 0) {
    return { success: false, error: "Amount must be a positive number." };
  }

  const category =
    (formData.get("category")?.toString() as ExpenseCategory) ?? "OTHER";
  const currency =
    formData.get("currency")?.toString().trim() || undefined;
  const taskId = formData.get("taskId")?.toString().trim() || null;
  const receiptDocumentId =
    formData.get("receiptDocumentId")?.toString().trim() || null;

  const markupStr = formData.get("markupPercent")?.toString().trim();
  const markupPercent =
    markupStr && markupStr !== "" ? parseFloat(markupStr) : null;

  const billableStr = formData.get("billable")?.toString();
  const billable = billableStr === "on" || billableStr === "true";

  const notes = formData.get("notes")?.toString().trim() || null;

  const body: CreateExpenseRequest = {
    date,
    description,
    amount,
    currency,
    category,
    taskId,
    receiptDocumentId,
    markupPercent,
    billable,
    notes,
  };

  try {
    await api.post<ExpenseResponse>(
      `/api/projects/${projectId}/expenses`,
      body,
    );
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}

export async function updateExpense(
  slug: string,
  projectId: string,
  expenseId: string,
  data: UpdateExpenseRequest,
): Promise<ActionResult> {
  try {
    await api.put<ExpenseResponse>(
      `/api/projects/${projectId}/expenses/${expenseId}`,
      data,
    );
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to edit this expense.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}

export async function deleteExpense(
  slug: string,
  projectId: string,
  expenseId: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/projects/${projectId}/expenses/${expenseId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete this expense.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}

export async function writeOffExpense(
  slug: string,
  projectId: string,
  expenseId: string,
): Promise<ActionResult> {
  try {
    await api.patch(
      `/api/projects/${projectId}/expenses/${expenseId}/write-off`,
      {},
    );
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}

export async function restoreExpense(
  slug: string,
  projectId: string,
  expenseId: string,
): Promise<ActionResult> {
  try {
    await api.patch(
      `/api/projects/${projectId}/expenses/${expenseId}/restore`,
      {},
    );
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}

export async function listExpenses(
  projectId: string,
  params?: {
    category?: ExpenseCategory;
    from?: string;
    to?: string;
    memberId?: string;
    page?: number;
    size?: number;
  },
): Promise<PaginatedExpenseResponse> {
  const searchParams = new URLSearchParams();
  if (params?.category) searchParams.set("category", params.category);
  if (params?.from) searchParams.set("from", params.from);
  if (params?.to) searchParams.set("to", params.to);
  if (params?.memberId) searchParams.set("memberId", params.memberId);
  if (params?.page != null) searchParams.set("page", String(params.page));
  if (params?.size != null) searchParams.set("size", String(params.size));
  searchParams.set("sort", "date,desc");

  const qs = searchParams.toString();
  const url = `/api/projects/${projectId}/expenses${qs ? `?${qs}` : ""}`;
  return api.get<PaginatedExpenseResponse>(url);
}

export async function getMyExpenses(params?: {
  page?: number;
  size?: number;
}): Promise<PaginatedExpenseResponse> {
  const searchParams = new URLSearchParams();
  if (params?.page != null) searchParams.set("page", String(params.page));
  if (params?.size != null) searchParams.set("size", String(params.size));
  searchParams.set("sort", "date,desc");

  const qs = searchParams.toString();
  const url = `/api/expenses/mine${qs ? `?${qs}` : ""}`;
  return api.get<PaginatedExpenseResponse>(url);
}
