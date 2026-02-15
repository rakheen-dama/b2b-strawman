"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CreateTagRequest,
  UpdateTagRequest,
  EntityType,
  TagResponse,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface CreateTagActionResult {
  success: boolean;
  error?: string;
  tag?: TagResponse;
}

export async function createTagAction(
  slug: string,
  req: CreateTagRequest,
): Promise<CreateTagActionResult> {
  let tag: TagResponse;
  try {
    tag = await api.post<TagResponse>("/api/tags", req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create tags.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A tag with this name already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/tags`);
  return { success: true, tag };
}

export async function updateTagAction(
  slug: string,
  id: string,
  req: UpdateTagRequest,
): Promise<ActionResult> {
  try {
    await api.put(`/api/tags/${id}`, req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update tags.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A tag with this name already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/tags`);
  return { success: true };
}

export async function deleteTagAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/tags/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete tags.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/tags`);
  return { success: true };
}

export async function setEntityTagsAction(
  slug: string,
  entityType: EntityType,
  entityId: string,
  tagIds: string[],
): Promise<ActionResult> {
  const prefix = entityType.toLowerCase() + "s";
  try {
    await api.post(`/api/${prefix}/${entityId}/tags`, { tagIds });
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to manage tags.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/${prefix}/${entityId}`);
  revalidatePath(`/org/${slug}/${prefix}`);
  return { success: true };
}
