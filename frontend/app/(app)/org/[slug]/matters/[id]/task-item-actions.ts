"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { TaskItem } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchTaskItems(taskId: string): Promise<TaskItem[]> {
  return api.get<TaskItem[]>(`/api/tasks/${taskId}/items`);
}

export async function addTaskItem(
  slug: string,
  projectId: string,
  taskId: string,
  title: string,
  sortOrder: number
): Promise<ActionResult> {
  try {
    await api.post<TaskItem>(`/api/tasks/${taskId}/items`, { title, sortOrder });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function toggleTaskItem(
  slug: string,
  projectId: string,
  taskId: string,
  itemId: string
): Promise<ActionResult> {
  try {
    await api.put<TaskItem>(`/api/tasks/${taskId}/items/${itemId}/toggle`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function updateTaskItem(
  slug: string,
  projectId: string,
  taskId: string,
  itemId: string,
  title: string,
  sortOrder: number
): Promise<ActionResult> {
  try {
    await api.put<TaskItem>(`/api/tasks/${taskId}/items/${itemId}`, {
      title,
      sortOrder,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function deleteTaskItem(
  slug: string,
  projectId: string,
  taskId: string,
  itemId: string
): Promise<ActionResult> {
  try {
    await api.delete(`/api/tasks/${taskId}/items/${itemId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}
