"use server";

import {
  api,
  ApiError,
  previewTemplate,
  uploadOrgLogo,
  deleteOrgLogo,
  getOrgSettings,
  updateOrgSettings,
} from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CreateTemplateRequest,
  UpdateTemplateRequest,
  TemplateDetailResponse,
  OrgSettings,
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

// ---- Generation Actions ----

export async function generateDocumentAction(
  templateId: string,
  entityId: string,
  saveToDocuments: boolean,
  acknowledgeWarnings: boolean = false,
  clauses?: Array<{ clauseId: string; sortOrder: number }>,
): Promise<{ success: boolean; data?: import("@/lib/types").GenerateDocumentResponse; pdfBase64?: string; error?: string }> {
  try {
    const { generateDocument } = await import("@/lib/api");
    const result = await generateDocument(templateId, entityId, saveToDocuments, acknowledgeWarnings, clauses);

    if (saveToDocuments) {
      return {
        success: true,
        data: result as import("@/lib/types").GenerateDocumentResponse,
      };
    }

    // Convert Blob to base64 for client transport
    const blob = result as Blob;
    const arrayBuffer = await blob.arrayBuffer();
    const base64 = Buffer.from(arrayBuffer).toString("base64");
    return { success: true, pdfBase64: base64 };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to generate document." };
  }
}

export async function fetchGeneratedDocumentsAction(
  entityType: import("@/lib/types").TemplateEntityType,
  entityId: string,
): Promise<{ success: boolean; data?: import("@/lib/types").GeneratedDocumentListResponse[]; error?: string }> {
  try {
    const { fetchGeneratedDocuments } = await import("@/lib/api");
    const data = await fetchGeneratedDocuments(entityType, entityId);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch generated documents." };
  }
}

export async function deleteGeneratedDocumentAction(
  id: string,
): Promise<{ success: boolean; error?: string }> {
  try {
    const { deleteGeneratedDocument } = await import("@/lib/api");
    await deleteGeneratedDocument(id);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to delete generated document." };
  }
}

export async function downloadGeneratedDocumentAction(
  id: string,
): Promise<{ success: boolean; pdfBase64?: string; fileName?: string; error?: string }> {
  try {
    const { downloadGeneratedDocument } = await import("@/lib/api");
    const blob = await downloadGeneratedDocument(id);
    const arrayBuffer = await blob.arrayBuffer();
    const base64 = Buffer.from(arrayBuffer).toString("base64");
    return { success: true, pdfBase64: base64 };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to download document." };
  }
}

// ---- Branding Actions ----

export async function uploadLogoAction(
  slug: string,
  formData: FormData,
): Promise<ActionResult> {
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

export async function deleteLogoAction(
  slug: string,
): Promise<ActionResult> {
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
  documentFooterText: string,
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
