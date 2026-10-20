"use server";

import { api, ApiError } from "@/lib/api";
import {
  addMatterMember,
  listMatterMembers,
  removeMatterMember,
  transferMatterLead,
} from "@/lib/api/matters";
import { revalidatePath } from "next/cache";
import type { MatterMember, OrgMember } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchProjectMembers(projectId: string): Promise<MatterMember[]> {
  return listMatterMembers(projectId);
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
    await addMatterMember(projectId, memberId);
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
    await removeMatterMember(projectId, memberId);
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
    await transferMatterLead(projectId, memberId);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to transfer lead role." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);

  return { success: true };
}
