"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { BudgetStatusResponse, UpsertBudgetRequest } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function upsertBudget(
  slug: string,
  projectId: string,
  data: UpsertBudgetRequest
): Promise<ActionResult> {
  try {
    await api.put<BudgetStatusResponse>(
      `/api/projects/${projectId}/budget`,
      data
    );
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to configure the budget.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}

export async function deleteBudget(
  slug: string,
  projectId: string
): Promise<ActionResult> {
  try {
    await api.delete(`/api/projects/${projectId}/budget`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete the budget.",
        };
      }
      if (error.status === 404) {
        revalidatePath(`/org/${slug}/projects/${projectId}`);
        return { success: true };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}
