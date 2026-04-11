"use server";

import { api, ApiError } from "@/lib/api";

export interface ModuleStatus {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
}

export interface ModuleSettingsResponse {
  modules: ModuleStatus[];
}

export interface ActionResult<T = void> {
  success: boolean;
  data?: T;
  error?: string;
}

/**
 * Fetch the list of horizontal modules and their enabled state.
 * Backend filters to horizontal modules only.
 *
 * Errors are intentionally NOT swallowed — a backend failure (or 403 for a
 * non-admin) should bubble up to the Next.js error boundary instead of being
 * masked as an empty list. The Settings → Features page is admin-only by
 * navigation gating, so a 403 here would indicate either an out-of-band link
 * or a misconfigured role.
 */
export async function getModuleSettings(): Promise<ModuleSettingsResponse> {
  return api.get<ModuleSettingsResponse>("/api/settings/modules");
}

/**
 * Update the set of enabled horizontal modules.
 * Empty array is valid (disables all horizontal modules).
 * Returns the canonical updated module list on success.
 */
export async function updateModuleSettings(
  enabledModules: string[]
): Promise<ActionResult<ModuleSettingsResponse>> {
  try {
    await api.put("/api/settings/modules", { enabledModules });
    const updated = await api.get<ModuleSettingsResponse>("/api/settings/modules");
    return { success: true, data: updated };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to manage features.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
