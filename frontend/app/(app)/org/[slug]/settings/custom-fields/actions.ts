"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CreateFieldDefinitionRequest,
  UpdateFieldDefinitionRequest,
  CreateFieldGroupRequest,
  UpdateFieldGroupRequest,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function createFieldDefinitionAction(
  slug: string,
  req: CreateFieldDefinitionRequest,
): Promise<ActionResult> {
  try {
    await api.post("/api/field-definitions", req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create field definitions.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A field definition with this slug already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/custom-fields`);
  return { success: true };
}

export async function updateFieldDefinitionAction(
  slug: string,
  id: string,
  req: UpdateFieldDefinitionRequest,
): Promise<ActionResult> {
  try {
    await api.put(`/api/field-definitions/${id}`, req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update field definitions.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A field definition with this slug already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/custom-fields`);
  return { success: true };
}

export async function deleteFieldDefinitionAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/field-definitions/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete field definitions.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/custom-fields`);
  return { success: true };
}

export async function createFieldGroupAction(
  slug: string,
  req: CreateFieldGroupRequest,
): Promise<ActionResult> {
  try {
    await api.post("/api/field-groups", req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create field groups.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A field group with this slug already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/custom-fields`);
  return { success: true };
}

export async function updateFieldGroupAction(
  slug: string,
  id: string,
  req: UpdateFieldGroupRequest,
): Promise<ActionResult> {
  try {
    await api.put(`/api/field-groups/${id}`, req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update field groups.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A field group with this slug already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/custom-fields`);
  return { success: true };
}

export async function deleteFieldGroupAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/field-groups/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete field groups.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/custom-fields`);
  return { success: true };
}
