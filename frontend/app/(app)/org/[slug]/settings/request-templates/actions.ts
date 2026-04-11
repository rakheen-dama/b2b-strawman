"use server";

import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  createRequestTemplate,
  updateRequestTemplate,
  deactivateRequestTemplate,
  duplicateRequestTemplate,
  type RequestTemplateResponse,
  type CreateRequestTemplateRequest,
  type UpdateRequestTemplateRequest,
} from "@/lib/api/information-requests";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: RequestTemplateResponse;
}

export async function createTemplateAction(
  slug: string,
  input: CreateRequestTemplateRequest
): Promise<ActionResult> {
  try {
    const data = await createRequestTemplate(input);
    revalidatePath(`/org/${slug}/settings/request-templates`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can create request templates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A request template with this name already exists.",
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
  input: UpdateRequestTemplateRequest
): Promise<ActionResult> {
  try {
    const data = await updateRequestTemplate(id, input);
    revalidatePath(`/org/${slug}/settings/request-templates`);
    revalidatePath(`/org/${slug}/settings/request-templates/${id}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update request templates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A request template with this name already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function deactivateTemplateAction(slug: string, id: string): Promise<ActionResult> {
  try {
    await deactivateRequestTemplate(id);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can deactivate request templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/request-templates`);
  return { success: true };
}

export async function duplicateTemplateAction(slug: string, id: string): Promise<ActionResult> {
  try {
    const data = await duplicateRequestTemplate(id);
    revalidatePath(`/org/${slug}/settings/request-templates`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can duplicate request templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
