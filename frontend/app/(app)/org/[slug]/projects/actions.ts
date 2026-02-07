"use server";

import { auth } from "@clerk/nextjs/server";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import type {
  Project,
  CreateProjectRequest,
  UpdateProjectRequest,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function createProject(
  slug: string,
  formData: FormData,
): Promise<ActionResult> {
  const name = formData.get("name")?.toString().trim() ?? "";
  const description = formData.get("description")?.toString().trim() || undefined;

  if (!name) {
    return { success: false, error: "Project name is required." };
  }

  const body: CreateProjectRequest = { name, description };

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
  formData: FormData,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "You must be an admin to edit projects." };
  }

  const name = formData.get("name")?.toString().trim() ?? "";
  const description = formData.get("description")?.toString().trim() || undefined;

  if (!name) {
    return { success: false, error: "Project name is required." };
  }

  const body: UpdateProjectRequest = { name, description };

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
  id: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:owner") {
    return { success: false, error: "Only organization owners can delete projects." };
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
