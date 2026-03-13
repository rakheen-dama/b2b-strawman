"use server";

import { api, ApiError } from "@/lib/api";

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

export async function generateDocxAction(
  templateId: string,
  entityId: string,
  outputFormat: string,
): Promise<{ success: boolean; data?: import("@/lib/types").GenerateDocxResult; error?: string }> {
  try {
    const data = await api.post<import("@/lib/types").GenerateDocxResult>(
      `/api/templates/${templateId}/generate-docx`,
      { entityId, outputFormat },
    );
    return { success: true, data };
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

export async function downloadDocxGeneratedDocumentAction(
  id: string,
): Promise<{ success: boolean; docxBase64?: string; fileName?: string; error?: string }> {
  try {
    const { downloadDocxGeneratedDocument } = await import("@/lib/api");
    const blob = await downloadDocxGeneratedDocument(id);
    const arrayBuffer = await blob.arrayBuffer();
    const base64 = Buffer.from(arrayBuffer).toString("base64");
    return { success: true, docxBase64: base64 };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to download DOCX document." };
  }
}
