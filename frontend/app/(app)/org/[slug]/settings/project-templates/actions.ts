"use server";

import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  deleteProjectTemplate,
  duplicateProjectTemplate,
  createProjectTemplate,
  updateProjectTemplate,
  saveProjectFromTemplate,
  instantiateProjectTemplate,
  updateTemplateRequiredCustomerFields,
} from "@/lib/api/templates";
import type {
  ProjectTemplateResponse,
  CreateProjectTemplateRequest,
  UpdateProjectTemplateRequest,
  SaveFromProjectRequest,
  InstantiateTemplateRequest,
} from "@/lib/api/templates";

// NOTE: `slug` is accepted as a client parameter in all actions below and used
// solely for `revalidatePath`. Tenant isolation is enforced by the backend via
// the auth token — a mismatched slug would only invalidate the wrong cache path,
// not grant cross-tenant access. This pattern is consistent across all Next.js
// server actions in this codebase.
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

export async function saveAsTemplateAction(
  slug: string,
  projectId: string,
  data: SaveFromProjectRequest,
): Promise<ActionResult> {
  try {
    const created = await saveProjectFromTemplate(projectId, data);
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

export async function updateRequiredCustomerFieldsAction(
  slug: string,
  templateId: string,
  fieldDefinitionIds: string[],
): Promise<{ success: boolean; error?: string }> {
  try {
    await updateTemplateRequiredCustomerFields(templateId, fieldDefinitionIds);
    revalidatePath(`/org/${slug}/settings/project-templates/${templateId}`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to configure template fields.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function instantiateTemplateAction(
  slug: string,
  templateId: string,
  data: InstantiateTemplateRequest,
): Promise<{ success: boolean; error?: string; projectId?: string }> {
  try {
    const result = await instantiateProjectTemplate(templateId, data);
    revalidatePath(`/org/${slug}/projects`);
    return { success: true, projectId: result.id };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error:
            "You do not have permission to create projects from templates.",
        };
      }
      if (error.status === 400) {
        return {
          success: false,
          error: error.message || "Template is inactive or invalid.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
