"use server";

import { auth } from "@clerk/nextjs/server";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { Task, CreateTaskRequest } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchTasks(
  projectId: string,
  filters?: { status?: string; assigneeId?: string; priority?: string }
): Promise<Task[]> {
  const params = new URLSearchParams();
  if (filters?.status) params.set("status", filters.status);
  if (filters?.assigneeId) params.set("assigneeId", filters.assigneeId);
  if (filters?.priority) params.set("priority", filters.priority);

  const query = params.toString();
  const url = `/api/projects/${projectId}/tasks${query ? `?${query}` : ""}`;

  return api.get<Task[]>(url);
}

export async function createTask(
  slug: string,
  projectId: string,
  formData: FormData
): Promise<ActionResult> {
  // Backend enforces access via ProjectAccessService — any project member can create tasks
  const title = formData.get("title")?.toString().trim() ?? "";
  if (!title) {
    return { success: false, error: "Task title is required." };
  }

  const description = formData.get("description")?.toString().trim() || undefined;
  const priority = formData.get("priority")?.toString() || undefined;
  const type = formData.get("type")?.toString().trim() || undefined;
  const dueDate = formData.get("dueDate")?.toString() || undefined;

  const body: CreateTaskRequest = { title, description, priority: priority as CreateTaskRequest["priority"], type, dueDate };

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

// Backend enforces update permissions: assignee can update their own task,
// lead/admin/owner can update any task in the project.
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
  const { orgRole } = await auth();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return { success: false, error: "Only admins and owners can delete tasks." };
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

export async function claimTask(
  slug: string,
  taskId: string,
  projectId: string
): Promise<ActionResult> {
  try {
    await api.post<Task>(`/api/tasks/${taskId}/claim`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 400 || error.status === 409) {
        return { success: false, error: "This task was just claimed by someone else. Please refresh." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function releaseTask(
  slug: string,
  taskId: string,
  projectId: string
): Promise<ActionResult> {
  try {
    await api.post<Task>(`/api/tasks/${taskId}/release`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}
