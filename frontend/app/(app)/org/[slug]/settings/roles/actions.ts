"use server";

import { revalidatePath } from "next/cache";
import {
  createOrgRole,
  updateOrgRole,
  deleteOrgRole,
} from "@/lib/api/org-roles";
import { ApiError } from "@/lib/api";
import type { CreateOrgRoleRequest, UpdateOrgRoleRequest } from "@/lib/api/org-roles";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function createRoleAction(
  slug: string,
  data: CreateOrgRoleRequest,
): Promise<ActionResult> {
  try {
    await createOrgRole(data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create roles.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A role with this name already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/roles`);
  return { success: true };
}

export async function updateRoleAction(
  slug: string,
  id: string,
  data: UpdateOrgRoleRequest,
): Promise<ActionResult> {
  try {
    await updateOrgRole(id, data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update roles.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A role with this name already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/roles`);
  return { success: true };
}

export async function deleteRoleAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await deleteOrgRole(id);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete roles.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/roles`);
  return { success: true };
}
