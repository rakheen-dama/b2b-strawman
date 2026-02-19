"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { ChecklistTemplateResponse } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function cloneChecklistTemplate(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.post<ChecklistTemplateResponse>(`/api/checklist-templates/${id}/clone`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can clone checklist templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/checklists`);
  return { success: true };
}

export async function deactivateChecklistTemplate(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/checklist-templates/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can deactivate checklist templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/checklists`);
  return { success: true };
}
