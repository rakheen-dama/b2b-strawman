"use server";

import { getAuthContext } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import type {
  Project,
  Customer,
  CreateProjectRequest,
  UpdateProjectRequest,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function createProject(
  slug: string,
  formData: FormData
): Promise<ActionResult> {
  const name = formData.get("name")?.toString().trim() ?? "";
  const description =
    formData.get("description")?.toString().trim() || undefined;
  const customerId =
    formData.get("customerId")?.toString().trim() || undefined;
  const dueDate = formData.get("dueDate")?.toString().trim() || undefined;

  if (!name) {
    return { success: false, error: "Project name is required." };
  }

  const body: CreateProjectRequest = { name, description, customerId, dueDate };

  try {
    await api.post<Project>("/api/projects", body);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function updateProject(
  slug: string,
  id: string,
  formData: FormData
): Promise<ActionResult> {
  const name = formData.get("name")?.toString().trim() ?? "";
  const description =
    formData.get("description")?.toString().trim() || undefined;
  const customerIdRaw = formData.get("customerId")?.toString().trim();
  const customerId = customerIdRaw === "" ? null : customerIdRaw ?? undefined;
  const dueDateRaw = formData.get("dueDate")?.toString().trim();
  const dueDate = dueDateRaw === "" ? null : dueDateRaw ?? undefined;

  if (!name) {
    return { success: false, error: "Project name is required." };
  }

  const body: UpdateProjectRequest = {
    name,
    description,
    customerId,
    dueDate,
  };

  try {
    await api.put<Project>(`/api/projects/${id}`, body);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects`);
  revalidatePath(`/org/${slug}/projects/${id}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function deleteProject(
  slug: string,
  id: string
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only organization owners can delete projects.",
    };
  }

  try {
    await api.delete(`/api/projects/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects`);
  revalidatePath(`/org/${slug}/dashboard`);

  redirect(`/org/${slug}/projects`);
}

export async function completeProject(
  slug: string,
  projectId: string,
  acknowledgeUnbilledTime?: boolean
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can complete projects.",
    };
  }

  try {
    const body = acknowledgeUnbilledTime
      ? { acknowledgeUnbilledTime }
      : undefined;
    await api.patch<Project>(`/api/projects/${projectId}/complete`, body);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects`);
  revalidatePath(`/org/${slug}/projects/${projectId}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function archiveProject(
  slug: string,
  projectId: string
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can archive projects.",
    };
  }

  try {
    await api.patch<Project>(`/api/projects/${projectId}/archive`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects`);
  revalidatePath(`/org/${slug}/projects/${projectId}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function reopenProject(
  slug: string,
  projectId: string
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can reopen projects.",
    };
  }

  try {
    await api.patch<Project>(`/api/projects/${projectId}/reopen`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects`);
  revalidatePath(`/org/${slug}/projects/${projectId}`);
  revalidatePath(`/org/${slug}/dashboard`);

  return { success: true };
}

export async function fetchActiveCustomers(): Promise<Customer[]> {
  return api.get<Customer[]>("/api/customers?status=ACTIVE");
}
