"use server";

import { ApiError } from "@/lib/api";
import { invokeContractReview, invokeDrafting } from "@/lib/api/ai";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { revalidatePath } from "next/cache";
import type { ContractReviewResponse, DraftingResponse } from "@/lib/api/ai";

interface ContractReviewActionResult {
  success: boolean;
  error?: string;
  data?: ContractReviewResponse;
}

export async function invokeContractReviewAction(
  slug: string,
  projectId: string,
  documentId: string
): Promise<ContractReviewActionResult> {
  // Capability guard — defense in depth (backend also enforces)
  const caps = await fetchMyCapabilities();
  if (!caps.capabilities.includes("AI_EXECUTE")) {
    return { success: false, error: "You do not have permission to invoke AI skills." };
  }

  try {
    const result = await invokeContractReview(documentId, projectId);
    revalidatePath(`/org/${slug}/projects/${projectId}`);
    if (result.status === "FAILED") {
      return {
        success: false,
        error: "Contract review failed. Check execution history for details.",
      };
    }
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "AI budget exhausted or skill not permitted. Check AI settings.",
        };
      }
      if (error.status === 422) {
        return {
          success: false,
          error:
            error.message ||
            "Review pre-flight check failed. Ensure the document is a PDF or DOCX file.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred. Please try again." };
  }
}

// ---- Drafting Action ----

interface DraftingActionResult {
  success: boolean;
  error?: string;
  data?: DraftingResponse;
}

export async function invokeDraftingAction(
  slug: string,
  projectId: string,
  templateId: string
): Promise<DraftingActionResult> {
  // Capability guard — defense in depth (backend also enforces)
  const caps = await fetchMyCapabilities();
  if (!caps.capabilities.includes("AI_EXECUTE")) {
    return { success: false, error: "You do not have permission to invoke AI skills." };
  }

  try {
    const result = await invokeDrafting(templateId, projectId);
    revalidatePath(`/org/${slug}/projects/${projectId}`);
    if (result.status === "FAILED") {
      return {
        success: false,
        error: "Drafting failed. Check execution history for details.",
      };
    }
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "AI budget exhausted or skill not permitted. Check AI settings.",
        };
      }
      if (error.status === 422) {
        return {
          success: false,
          error:
            error.message ||
            "Drafting pre-flight check failed. Ensure the template and matter are valid.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred. Please try again." };
  }
}
