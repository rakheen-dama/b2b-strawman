"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CreateSavedViewRequest,
  UpdateSavedViewRequest,
  SavedViewResponse,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function createSavedViewAction(
  slug: string,
  req: CreateSavedViewRequest,
): Promise<ActionResult> {
  try {
    await api.post<SavedViewResponse>("/api/views", req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create shared views.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects`);
  return { success: true };
}

export async function updateSavedViewAction(
  slug: string,
  viewId: string,
  req: UpdateSavedViewRequest,
): Promise<ActionResult> {
  try {
    await api.put<SavedViewResponse>(`/api/views/${viewId}`, req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to edit this view.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects`);
  return { success: true };
}

export async function deleteSavedViewAction(
  slug: string,
  viewId: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/views/${viewId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete this view.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects`);
  return { success: true };
}
