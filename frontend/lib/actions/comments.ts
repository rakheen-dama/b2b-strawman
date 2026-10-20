"use server";

import { ApiError } from "@/lib/api";
import {
  createMatterComment,
  deleteMatterComment,
  listMatterComments,
  updateMatterComment,
} from "@/lib/api/matters";
import { revalidatePath } from "next/cache";
import type { Comment } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchComments(
  projectId: string,
  entityType: string,
  entityId: string
): Promise<Comment[]> {
  return listMatterComments(projectId, { entityType, entityId });
}

export async function fetchProjectComments(projectId: string): Promise<Comment[]> {
  return listMatterComments(projectId, { entityType: "PROJECT", size: 50 });
}

export async function createProjectComment(
  orgSlug: string,
  projectId: string,
  body: string
): Promise<ActionResult> {
  if (!body.trim()) {
    return { success: false, error: "Comment cannot be empty." };
  }

  try {
    await createMatterComment(projectId, {
      entityType: "PROJECT",
      entityId: projectId,
      body: body.trim(),
      visibility: "SHARED",
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
    await createMatterComment(projectId, {
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
    await updateMatterComment(projectId, commentId, {
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
    await deleteMatterComment(projectId, commentId);
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
