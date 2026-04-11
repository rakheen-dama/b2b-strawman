"use server";

import {
  api,
  ApiError,
  uploadOrgLogo,
  deleteOrgLogo,
  getOrgSettings,
  updateOrgSettings,
  uploadDocxTemplate,
  replaceDocxFile,
  getDocxFields,
  downloadDocxTemplate,
} from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  TemplateDetailResponse,
  UpdateOrgSettingsRequest,
  Project,
  Customer,
  InvoiceResponse,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: TemplateDetailResponse;
}

// ---- DOCX Template Actions ----

export async function uploadDocxTemplateAction(
  slug: string,
  formData: FormData
): Promise<ActionResult> {
  try {
    const file = formData.get("file") as File;
    const name = formData.get("name") as string;
    const category = formData.get("category") as string;
    const entityType = formData.get("entityType") as string;
    const description = formData.get("description") as string | null;

    if (!file || !name || !category || !entityType) {
      return { success: false, error: "Missing required fields." };
    }

    const data = await uploadDocxTemplate(
      file,
      name,
      category,
      entityType,
      description || undefined
    );
    revalidatePath(`/org/${slug}/settings/templates`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to upload templates.",
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

export async function getDocxFieldsAction(
  templateId: string
): Promise<{ success: boolean; data?: import("@/lib/types").DiscoveredField[]; error?: string }> {
  try {
    const data = await getDocxFields(templateId);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch template fields." };
  }
}

export async function downloadDocxTemplateAction(
  templateId: string
): Promise<{ success: boolean; url?: string; error?: string }> {
  try {
    const url = await downloadDocxTemplate(templateId);
    return { success: true, url };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to download template." };
  }
}

export async function replaceDocxFileAction(
  slug: string,
  id: string,
  formData: FormData
): Promise<ActionResult> {
  try {
    const file = formData.get("file") as File;
    if (!file) {
      return { success: false, error: "No file provided." };
    }

    const data = await replaceDocxFile(id, file);
    revalidatePath(`/org/${slug}/settings/templates`);
    revalidatePath(`/org/${slug}/settings/templates/${id}/edit`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to replace template files.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

// ---- Branding Actions ----

export async function uploadLogoAction(slug: string, formData: FormData): Promise<ActionResult> {
  try {
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

export async function deleteLogoAction(slug: string): Promise<ActionResult> {
  try {
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
  documentFooterText: string
): Promise<ActionResult> {
  try {
    // Get current settings to preserve defaultCurrency
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

// ---- Field Pack Linkage Action ----

export interface FieldPackStatus {
  packId: string;
  packName: string;
  applied: boolean;
  missingFields: string[];
}

export async function fetchRequiredFieldPacksAction(
  templateId: string
): Promise<{ success: boolean; data?: FieldPackStatus[]; error?: string }> {
  try {
    const data = await api.get<FieldPackStatus[]>(
      `/api/templates/${templateId}/required-field-packs`
    );
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch field pack status." };
  }
}

// ---- Variable Metadata Action ----

export async function fetchVariableMetadataAction(
  entityType: import("@/lib/types").TemplateEntityType
): Promise<import("@/components/editor/actions").VariableMetadataResponse> {
  const { fetchVariableMetadata } = await import("@/components/editor/actions");
  return fetchVariableMetadata(entityType);
}

// ---- Entity Picker Actions ----

export async function fetchProjectsForPicker(): Promise<Project[]> {
  return api.get<Project[]>("/api/projects");
}

export async function fetchCustomersForPicker(): Promise<Customer[]> {
  return api.get<Customer[]>("/api/customers");
}

export async function fetchInvoicesForPicker(): Promise<InvoiceResponse[]> {
  return api.get<InvoiceResponse[]>("/api/invoices");
}
