"use server";

import { auth } from "@clerk/nextjs/server";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { Project, CustomerProject } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchCustomerProjects(customerId: string): Promise<Project[]> {
  return api.get<Project[]>(`/api/customers/${customerId}/projects`);
}

export async function fetchProjects(): Promise<Project[]> {
  return api.get<Project[]>("/api/projects");
}

export async function linkProject(
  slug: string,
  customerId: string,
  projectId: string
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can link projects." };
  }

  try {
    await api.post<CustomerProject>(`/api/customers/${customerId}/projects/${projectId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);

  return { success: true };
}

export async function unlinkProject(
  slug: string,
  customerId: string,
  projectId: string
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can unlink projects." };
  }

  try {
    await api.delete(`/api/customers/${customerId}/projects/${projectId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);

  return { success: true };
}
