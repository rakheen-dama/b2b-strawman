"use server";

import { api, ApiError } from "@/lib/api";
import { uploadOrgLogo, deleteOrgLogo } from "@/lib/api/settings";
import { revalidatePath } from "next/cache";
import type { UpdateOrgSettingsRequest, UpdateTaxSettingsRequest } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateGeneralSettings(
  slug: string,
  data: UpdateOrgSettingsRequest
): Promise<ActionResult> {
  try {
    await api.put("/api/settings", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/general`);
  return { success: true };
}

export async function updateGeneralTaxSettings(
  slug: string,
  data: UpdateTaxSettingsRequest
): Promise<ActionResult> {
  try {
    await api.patch("/api/settings/tax", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 405) {
        return { success: false, error: "Tax settings API not available yet." };
      }
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update tax settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/general`);
  return { success: true };
}

export async function uploadLogoAction(slug: string, formData: FormData): Promise<ActionResult> {
  const file = formData.get("file") as File;
  if (!file) {
    return { success: false, error: "No file provided." };
  }

  try {
    await uploadOrgLogo(file);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 413) {
        return { success: false, error: "Logo file is too large. Max 2 MB." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/general`);
  return { success: true };
}

export async function deleteLogoAction(slug: string): Promise<ActionResult> {
  try {
    await deleteOrgLogo();
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/general`);
  return { success: true };
}
