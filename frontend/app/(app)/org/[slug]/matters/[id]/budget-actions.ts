"use server";

import { ApiError } from "@/lib/api";
import { deleteMatterBudget, upsertMatterBudget } from "@/lib/api/matters";
import { revalidatePath } from "next/cache";
import type { UpsertBudgetRequest } from "@/lib/types";

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
    await upsertMatterBudget(projectId, data);
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

export async function deleteBudget(slug: string, projectId: string): Promise<ActionResult> {
  try {
    await deleteMatterBudget(projectId);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete the budget.",
        };
      }
      if (error.status === 404) {
        // Already deleted, treat as success
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
