"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

export interface Comment {
  id: string;
  entityType: string;
  entityId: string;
  projectId: string;
  authorMemberId: string;
  authorName: string | null;
  authorAvatarUrl: string | null;
  body: string;
  visibility: "INTERNAL" | "SHARED";
  parentId: string | null;
  createdAt: string;
  updatedAt: string;
}

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchComments(
  projectId: string,
  entityType: string,
  entityId: string
): Promise<Comment[]> {
  return api.get<Comment[]>(
    `/api/projects/${projectId}/comments?entityType=${encodeURIComponent(entityType)}&entityId=${encodeURIComponent(entityId)}`
  );
}

export async function createComment(
  orgSlug: string,
  projectId: string,
  entityType: string,
  entityId: string,
  body: string,
  visibility?: string
): Promise<ActionResult> {
  if (!body.trim()) {
    return { success: false, error: "Comment cannot be empty." };
  }

  try {
    await api.post(`/api/projects/${projectId}/comments`, {
      entityType,
      entityId,
      body: body.trim(),
      ...(visibility ? { visibility } : {}),
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/projects/${projectId}`);

  return { success: true };
}

export async function updateComment(
  orgSlug: string,
  projectId: string,
  commentId: string,
  body: string,
  visibility?: string
): Promise<ActionResult> {
  if (!body.trim()) {
    return { success: false, error: "Comment cannot be empty." };
  }

  try {
    await api.put(`/api/projects/${projectId}/comments/${commentId}`, {
      body: body.trim(),
      ...(visibility ? { visibility } : {}),
    });
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to edit this comment.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/projects/${projectId}`);

  return { success: true };
}

export async function deleteComment(
  orgSlug: string,
  projectId: string,
  commentId: string
): Promise<ActionResult> {
  try {
    await api.delete(`/api/projects/${projectId}/comments/${commentId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete this comment.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/projects/${projectId}`);

  return { success: true };
}
