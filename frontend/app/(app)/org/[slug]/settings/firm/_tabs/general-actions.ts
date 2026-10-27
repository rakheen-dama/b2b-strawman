"use server";

import { api, ApiError } from "@/lib/api";
import { uploadOrgLogo, deleteOrgLogo, updateLetterheadSettings } from "@/lib/api/settings";
import { revalidatePath } from "next/cache";
import type {
  LetterheadSettings,
  UpdateLetterheadSettingsRequest,
  UpdateOrgSettingsRequest,
  UpdateTaxSettingsRequest,
} from "@/lib/types";

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
  revalidatePath(`/org/${slug}/settings/firm`);
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
  revalidatePath(`/org/${slug}/settings/firm`);
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
  revalidatePath(`/org/${slug}/settings/firm`);
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
  revalidatePath(`/org/${slug}/settings/firm`);
  return { success: true };
}

export interface LetterheadActionResult extends ActionResult {
  /** The saved letterhead as the server stored it (trimmed, blank → null). */
  letterhead?: LetterheadSettings;
  /** Per-field messages from a 400 ProblemDetail (`fieldErrors`). */
  fieldErrors?: Array<{ field: string; message: string }>;
}

/**
 * Full replace of the firm letterhead (Phase 88 U8, ADR-353). Admin/owner only — enforced by the
 * backend (TEAM_OVERSIGHT + service admin/owner check); a 403 maps to a friendly message.
 */
export async function updateLetterheadSettingsAction(
  slug: string,
  data: UpdateLetterheadSettingsRequest
): Promise<LetterheadActionResult> {
  let letterhead: LetterheadSettings | undefined;
  try {
    const settings = await updateLetterheadSettings(data);
    letterhead = settings.letterhead;
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Only owners and admins can update the letterhead." };
      }
      if (error.status === 400) {
        // GlobalExceptionHandler puts [{ field, message }] on the ProblemDetail's `fieldErrors`.
        const fieldErrors = error.detail?.fieldErrors as
          | Array<{ field: string; message: string }>
          | undefined;
        return { success: false, error: error.message, fieldErrors };
      }
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/firm`);
  return { success: true, letterhead };
}
