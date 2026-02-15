"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CreateTemplateRequest,
  UpdateTemplateRequest,
  TemplateDetailResponse,
  OrgSettings,
  UpdateOrgSettingsRequest,
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
  active: boolean,
): Promise<ActionResult> {
  try {
    // We need to get the current template first to preserve name/content
    const current = await api.get<TemplateDetailResponse>(
      `/api/templates/${id}`,
    );
    await api.put<TemplateDetailResponse>(`/api/templates/${id}`, {
      name: current.name,
      description: current.description,
      content: current.content,
      css: current.css,
      active,
    });
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
): Promise<{ success: boolean; html?: string; error?: string }> {
  try {
    const { previewTemplate } = await import("@/lib/api");
    const html = await previewTemplate(id, entityId);
    return { success: true, html };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to generate preview." };
  }
}

// ---- Branding Actions ----

export async function uploadLogoAction(
  slug: string,
  formData: FormData,
): Promise<ActionResult> {
  try {
    const { uploadOrgLogo } = await import("@/lib/api");
    const file = formData.get("file") as File;
    if (!file) {
      return { success: false, error: "No file provided." };
    }
    await uploadOrgLogo(file);
    revalidatePath(`/org/${slug}/settings/templates`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to upload logo." };
  }
}

export async function deleteLogoAction(
  slug: string,
): Promise<ActionResult> {
  try {
    const { deleteOrgLogo } = await import("@/lib/api");
    await deleteOrgLogo();
    revalidatePath(`/org/${slug}/settings/templates`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to delete logo." };
  }
}

export async function saveBrandingAction(
  slug: string,
  brandColor: string,
  documentFooterText: string,
): Promise<ActionResult> {
  try {
    // Get current settings to preserve defaultCurrency
    const { getOrgSettings, updateOrgSettings } = await import("@/lib/api");
    const current = await getOrgSettings();
    await updateOrgSettings({
      defaultCurrency: current.defaultCurrency,
      brandColor,
      documentFooterText,
    } as UpdateOrgSettingsRequest);
    revalidatePath(`/org/${slug}/settings/templates`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to save branding settings." };
  }
}
