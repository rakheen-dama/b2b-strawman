"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { ChecklistTemplateResponse } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: ChecklistTemplateResponse;
}

export interface CreateChecklistTemplateInput {
  name: string;
  description?: string;
  customerType: string;
  autoInstantiate: boolean;
  items: {
    name: string;
    description?: string;
    sortOrder: number;
    required: boolean;
    requiresDocument: boolean;
    requiredDocumentLabel?: string;
  }[];
}

export async function createChecklistTemplate(
  slug: string,
  input: CreateChecklistTemplateInput,
): Promise<ActionResult> {
  try {
    const data = await api.post<ChecklistTemplateResponse>(
      "/api/checklist-templates",
      input,
    );
    revalidatePath(`/org/${slug}/settings/checklists`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can create checklist templates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A checklist template with this name already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
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

export type UpdateChecklistTemplateInput = CreateChecklistTemplateInput;

export async function updateChecklistTemplate(
  slug: string,
  id: string,
  data: UpdateChecklistTemplateInput,
): Promise<ActionResult> {
  try {
    await api.put(`/api/checklist-templates/${id}`, data);
    revalidatePath(`/org/${slug}/settings/checklists`);
    revalidatePath(`/org/${slug}/settings/checklists/${id}`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update checklist templates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A checklist template with this name already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
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
