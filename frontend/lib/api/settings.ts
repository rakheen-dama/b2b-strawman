import "server-only";

import { redirect } from "next/navigation";
import { api, ApiError, API_BASE, getAuthFetchOptions } from "./client";
import type {
  OrgSettings,
  UpdateOrgSettingsRequest,
} from "@/lib/types";

// ---- Org Settings (Branding) ----

export async function getOrgSettings(): Promise<OrgSettings> {
  return api.get<OrgSettings>("/api/settings");
}

/**
 * Server-side helper to check whether a horizontal module is enabled for the
 * current org. Used by gated server component pages to short-circuit data
 * fetching BEFORE invoking backend list endpoints.
 *
 * Falls back to `false` (treat as disabled) on any fetch error so we render
 * the disabled fallback rather than leaking data on transient failures.
 */
export async function isModuleEnabledServer(
  moduleId: string,
): Promise<boolean> {
  try {
    const settings = await getOrgSettings();
    return (settings.enabledModules ?? []).includes(moduleId);
  } catch (error) {
    console.error(
      `Failed to fetch org settings for module check (${moduleId}):`,
      error,
    );
    return false;
  }
}

export async function updateOrgSettings(
  req: UpdateOrgSettingsRequest,
): Promise<OrgSettings> {
  return api.put<OrgSettings>("/api/settings", req);
}

export async function uploadOrgLogo(file: File): Promise<OrgSettings> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("POST");
  } catch {
    redirect("/sign-in");
  }

  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE}/api/settings/logo`, {
    method: "POST",
    headers: {
      ...authOptions.headers,
    },
    body: formData,
    credentials: authOptions.credentials,
  });

  if (!response.ok) {
    let message = response.statusText;
    try {
      const detail = await response.json();
      message = detail?.detail || detail?.title || message;
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message);
  }

  return response.json() as Promise<OrgSettings>;
}

export async function deleteOrgLogo(): Promise<OrgSettings> {
  return api.delete<OrgSettings>("/api/settings/logo");
}
