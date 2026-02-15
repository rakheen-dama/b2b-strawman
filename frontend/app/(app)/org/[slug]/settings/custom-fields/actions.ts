"use server";

import { api, ApiError, setEntityFieldGroups } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CreateFieldDefinitionRequest,
  UpdateFieldDefinitionRequest,
  CreateFieldGroupRequest,
  UpdateFieldGroupRequest,
  EntityType,
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

// ---- Entity Custom Field Value Actions ----

export async function updateEntityCustomFieldsAction(
  slug: string,
  entityType: EntityType,
  entityId: string,
  customFields: Record<string, unknown>,
): Promise<ActionResult> {
  const prefix = entityType.toLowerCase() + "s";
  try {
    // Fetch the current entity first so we can send a full PUT body.
    // The backend PUT endpoints require all required fields (e.g. name).
    const current = await api.get<Record<string, unknown>>(`/api/${prefix}/${entityId}`);
    await api.put(`/api/${prefix}/${entityId}`, { ...current, customFields });
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to update custom fields." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/${prefix}/${entityId}`);
  revalidatePath(`/org/${slug}/${prefix}`);
  return { success: true };
}

export async function setEntityFieldGroupsAction(
  slug: string,
  entityType: EntityType,
  entityId: string,
  appliedFieldGroups: string[],
): Promise<ActionResult> {
  const prefix = entityType.toLowerCase() + "s";
  try {
    await setEntityFieldGroups(entityType, entityId, appliedFieldGroups);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to manage field groups." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/${prefix}/${entityId}`);
  revalidatePath(`/org/${slug}/${prefix}`);
  return { success: true };
}
