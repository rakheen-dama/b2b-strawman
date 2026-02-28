"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { ProjectMember, OrgMember } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchProjectMembers(
  projectId: string
): Promise<ProjectMember[]> {
  return api.get<ProjectMember[]>(`/api/projects/${projectId}/members`);
}

export async function fetchOrgMembers(): Promise<OrgMember[]> {
  return api.get<OrgMember[]>("/api/members");
}

export async function addProjectMember(
  slug: string,
  projectId: string,
  memberId: string
): Promise<ActionResult> {
  try {
    await api.post(`/api/projects/${projectId}/members`, { memberId });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to add member." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function removeProjectMember(
  slug: string,
  projectId: string,
  memberId: string
): Promise<ActionResult> {
  try {
    await api.delete(`/api/projects/${projectId}/members/${memberId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to remove member." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}

export async function transferLead(
  slug: string,
  projectId: string,
  memberId: string
): Promise<ActionResult> {
  try {
    await api.put(`/api/projects/${projectId}/members/${memberId}/role`, {
      role: "lead",
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to transfer lead role." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}
