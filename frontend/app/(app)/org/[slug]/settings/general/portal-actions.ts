"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

export type PortalDigestCadence = "WEEKLY" | "BIWEEKLY" | "OFF";
export type PortalRetainerMemberDisplay =
  | "FULL_NAME"
  | "FIRST_NAME_ROLE"
  | "ROLE_ONLY"
  | "ANONYMISED";

export interface PortalActionResult {
  success: boolean;
  error?: string;
}

/**
 * Updates the firm-wide portal digest cadence (Epic 498A / ADR-258). Admin-or-owner only.
 */
export async function updatePortalDigestCadence(
  slug: string,
  portalDigestCadence: PortalDigestCadence,
): Promise<PortalActionResult> {
  try {
    await api.patch("/api/settings/portal-digest-cadence", {
      portalDigestCadence,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update portal digest cadence.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/general`);
  return { success: true };
}

/**
 * Updates the firm-wide portal retainer member-display privacy mode (Epic 496A / ADR-255).
 * Admin-or-owner only.
 */
export async function updatePortalRetainerMemberDisplay(
  slug: string,
  portalRetainerMemberDisplay: PortalRetainerMemberDisplay,
): Promise<PortalActionResult> {
  try {
    await api.patch("/api/settings/portal-retainer-member-display", {
      portalRetainerMemberDisplay,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error:
            "Only admins and owners can update portal retainer member display.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/general`);
  return { success: true };
}
