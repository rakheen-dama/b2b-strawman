"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { PortalDigestCadence } from "@/lib/types/settings";

export interface PortalActionResult {
  success: boolean;
  error?: string;
}

/**
 * Updates the firm-wide portal digest cadence (Epic 498A / ADR-258). Admin-or-owner only.
 */
export async function updatePortalDigestCadence(
  slug: string,
  portalDigestCadence: PortalDigestCadence
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
  revalidatePath(`/org/${slug}/settings/firm`);
  return { success: true };
}
