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
