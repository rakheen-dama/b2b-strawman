"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

export interface TemplateClauseDetail {
  id: string;
  clauseId: string;
  title: string;
  slug: string;
  category: string;
  description: string | null;
  bodyPreview: string | null;
  required: boolean;
  sortOrder: number;
  active: boolean;
}

export interface TemplateClauseConfig {
  clauseId: string;
  sortOrder: number;
  required: boolean;
}

export interface TemplateClauseActionResult {
  success: boolean;
  error?: string;
}

export async function getTemplateClauses(
  templateId: string,
): Promise<TemplateClauseDetail[]> {
  try {
    return await api.get<TemplateClauseDetail[]>(
      `/api/templates/${templateId}/clauses`,
    );
  } catch (error) {
    console.error("Failed to fetch template clauses:", error);
    return [];
  }
}

export async function setTemplateClauses(
  templateId: string,
  clauses: TemplateClauseConfig[],
  slug?: string,
): Promise<TemplateClauseActionResult> {
  try {
    await api.put(`/api/templates/${templateId}/clauses`, { clauses });
    if (slug) {
      revalidatePath(`/org/${slug}/settings/templates/${templateId}/edit`);
    }
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update template clauses.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function addClauseToTemplate(
  templateId: string,
  clauseId: string,
  required: boolean,
  slug?: string,
): Promise<TemplateClauseActionResult> {
  try {
    await api.post(`/api/templates/${templateId}/clauses`, {
      clauseId,
      required,
    });
    if (slug) {
      revalidatePath(`/org/${slug}/settings/templates/${templateId}/edit`);
    }
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to add clauses to templates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "This clause is already associated with the template.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function removeClauseFromTemplate(
  templateId: string,
  clauseId: string,
  slug?: string,
): Promise<TemplateClauseActionResult> {
  try {
    await api.delete(`/api/templates/${templateId}/clauses/${clauseId}`);
    if (slug) {
      revalidatePath(`/org/${slug}/settings/templates/${templateId}/edit`);
    }
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to remove clauses from templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
