"use server";

import { getAuthContext } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { Task, CreateTaskRequest } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchTasks(
  projectId: string,
  filters?: {
    status?: string;
    assigneeId?: string;
    priority?: string;
  }
): Promise<Task[]> {
  const params = new URLSearchParams();
  if (filters?.status) params.set("status", filters.status);
  if (filters?.assigneeId) params.set("assigneeId", filters.assigneeId);
  if (filters?.priority) params.set("priority", filters.priority);

  const query = params.toString();
  const url = `/api/projects/${projectId}/tasks${query ? `?${query}` : ""}`;

  return api.get<Task[]>(url);
}

export async function fetchTask(taskId: string): Promise<Task> {
  return api.get<Task>(`/api/tasks/${taskId}`);
}

export async function createTask(
  slug: string,
  projectId: string,
  formData: FormData,
  assigneeId?: string | null
): Promise<ActionResult> {
  const title = formData.get("title")?.toString().trim() ?? "";
  if (!title) {
    return { success: false, error: "Task title is required." };
  }

  const description =
    formData.get("description")?.toString().trim() || undefined;
  const priority = formData.get("priority")?.toString() || undefined;
  const type = formData.get("type")?.toString().trim() || undefined;
  const dueDate = formData.get("dueDate")?.toString() || undefined;

  const body: CreateTaskRequest = {
    title,
    description,
    priority: priority as CreateTaskRequest["priority"],
    type,
    dueDate,
    assigneeId: assigneeId ?? undefined,
  };

  try {
    await api.post<Task>(`/api/projects/${projectId}/tasks`, body);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function updateTask(
  slug: string,
  taskId: string,
  projectId: string,
  data: Record<string, unknown>
): Promise<ActionResult> {
  try {
    await api.put<Task>(`/api/tasks/${taskId}`, data);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function deleteTask(
  slug: string,
  taskId: string,
  projectId: string
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return {
      success: false,
      error: "Only admins and owners can delete tasks.",
    };
  }

  try {
    await api.delete(`/api/tasks/${taskId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function completeTask(
  slug: string,
  taskId: string,
  projectId: string
): Promise<ActionResult> {
  try {
    await api.patch<Task>(`/api/tasks/${taskId}/complete`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}

export async function cancelTask(
  slug: string,
  taskId: string,
  projectId: string
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return {
      success: false,
      error: "Only admins and owners can cancel tasks.",
    };
  }

  try {
    await api.patch<Task>(`/api/tasks/${taskId}/cancel`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}

export async function reopenTask(
  slug: string,
  taskId: string,
  projectId: string
): Promise<ActionResult> {
  try {
    await api.patch<Task>(`/api/tasks/${taskId}/reopen`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  return { success: true };
}
