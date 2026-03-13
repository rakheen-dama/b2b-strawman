"use server";

import { api, ApiError, previewTemplate } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CreateTemplateRequest,
  UpdateTemplateRequest,
  TemplateDetailResponse,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: TemplateDetailResponse;
}

export async function createTemplateAction(
  slug: string,
  req: CreateTemplateRequest,
): Promise<ActionResult> {
  try {
    const data = await api.post<TemplateDetailResponse>("/api/templates", req);
    revalidatePath(`/org/${slug}/settings/templates`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create templates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A template with this slug already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function updateTemplateAction(
  slug: string,
  id: string,
  req: UpdateTemplateRequest,
): Promise<ActionResult> {
  try {
    const data = await api.put<TemplateDetailResponse>(
      `/api/templates/${id}`,
      req,
    );
    revalidatePath(`/org/${slug}/settings/templates`);
    revalidatePath(`/org/${slug}/settings/templates/${id}/edit`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function cloneTemplateAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    const data = await api.post<TemplateDetailResponse>(
      `/api/templates/${id}/clone`,
    );
    revalidatePath(`/org/${slug}/settings/templates`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to clone templates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A clone of this template already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function resetTemplateAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.post<void>(`/api/templates/${id}/reset`);
    revalidatePath(`/org/${slug}/settings/templates`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to reset templates.",
        };
      }
      if (error.status === 400) {
        return {
          success: false,
          error: "Only customized templates can be reset.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function deactivateTemplateAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/templates/${id}`);
    revalidatePath(`/org/${slug}/settings/templates`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to modify templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function previewTemplateAction(
  id: string,
  entityId: string,
  clauses?: Array<{ clauseId: string; sortOrder: number }>,
): Promise<{ success: boolean; html?: string; validationResult?: import("@/lib/types").TemplateValidationResult; error?: string }> {
  try {
    const result = await previewTemplate(id, entityId, clauses);
    return { success: true, html: result.html, validationResult: result.validationResult ?? undefined };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to generate preview." };
  }
}

