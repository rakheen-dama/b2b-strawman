"use server";

import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  deleteProjectTemplate,
  duplicateProjectTemplate,
  createProjectTemplate,
  updateProjectTemplate,
} from "@/lib/api/templates";
import type {
  ProjectTemplateResponse,
  CreateProjectTemplateRequest,
  UpdateProjectTemplateRequest,
} from "@/lib/api/templates";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: ProjectTemplateResponse;
}

export async function deleteTemplateAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await deleteProjectTemplate(id);
    revalidatePath(`/org/${slug}/settings/project-templates`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete templates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error:
            "Template has active schedules and cannot be deleted. Deactivate it instead.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function duplicateTemplateAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    const data = await duplicateProjectTemplate(id);
    revalidatePath(`/org/${slug}/settings/project-templates`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to duplicate templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function createProjectTemplateAction(
  slug: string,
  data: CreateProjectTemplateRequest,
): Promise<ActionResult> {
  try {
    const created = await createProjectTemplate(data);
    revalidatePath(`/org/${slug}/settings/project-templates`);
    return { success: true, data: created };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function updateProjectTemplateAction(
  slug: string,
  id: string,
  data: UpdateProjectTemplateRequest,
): Promise<ActionResult> {
  try {
    const updated = await updateProjectTemplate(id, data);
    revalidatePath(`/org/${slug}/settings/project-templates`);
    return { success: true, data: updated };
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
