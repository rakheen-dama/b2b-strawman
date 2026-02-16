"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CreateChecklistTemplateRequest,
  UpdateChecklistTemplateRequest,
  ChecklistTemplateWithItemsResponse,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function instantiateChecklist(
  slug: string,
  customerId: string,
  templateId: string,
): Promise<ActionResult> {
  try {
    await api.post(`/api/customers/${customerId}/checklists`, { templateId });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}

export async function completeChecklistItem(
  slug: string,
  customerId: string,
  itemId: string,
  notes?: string,
  documentId?: string,
): Promise<ActionResult> {
  try {
    await api.put(`/api/checklist-items/${itemId}/complete`, {
      ...(notes ? { notes } : {}),
      ...(documentId ? { documentId } : {}),
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}

export async function skipChecklistItem(
  slug: string,
  customerId: string,
  itemId: string,
  reason: string,
): Promise<ActionResult> {
  try {
    await api.put(`/api/checklist-items/${itemId}/skip`, { reason });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}

export async function reopenChecklistItem(
  slug: string,
  customerId: string,
  itemId: string,
): Promise<ActionResult> {
  try {
    await api.put(`/api/checklist-items/${itemId}/reopen`, {});
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}

export async function createChecklistTemplate(
  slug: string,
  req: CreateChecklistTemplateRequest,
): Promise<ActionResult> {
  try {
    await api.post("/api/checklist-templates", req);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/checklists`);
  return { success: true };
}

export async function updateChecklistTemplate(
  slug: string,
  templateId: string,
  req: UpdateChecklistTemplateRequest,
): Promise<ActionResult> {
  try {
    await api.put(`/api/checklist-templates/${templateId}`, req);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/checklists`);
  return { success: true };
}

export async function cloneChecklistTemplate(
  slug: string,
  templateId: string,
  newName: string,
): Promise<ActionResult> {
  try {
    await api.post(`/api/checklist-templates/${templateId}/clone`, { newName });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/checklists`);
  return { success: true };
}

export async function deactivateChecklistTemplate(
  slug: string,
  templateId: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/checklist-templates/${templateId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/checklists`);
  return { success: true };
}

export async function getChecklistTemplateWithItems(
  templateId: string,
): Promise<{ success: boolean; data?: ChecklistTemplateWithItemsResponse; error?: string }> {
  try {
    const data = await api.get<ChecklistTemplateWithItemsResponse>(
      `/api/checklist-templates/${templateId}`,
    );
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
