"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { Project, CreateProjectRequest } from "@/lib/types";

interface CreateProjectResult {
  success: boolean;
  error?: string;
}

export async function createProject(
  slug: string,
  formData: FormData,
): Promise<CreateProjectResult> {
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
