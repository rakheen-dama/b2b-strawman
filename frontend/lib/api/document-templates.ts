import "server-only";

import { redirect } from "next/navigation";
import { api, ApiError, API_BASE, getAuthFetchOptions } from "./client";
import type {
  TemplateListResponse,
  TemplateDetailResponse,
  CreateTemplateRequest,
  UpdateTemplateRequest,
  PreviewResponse,
} from "@/lib/types";

// ---- Document Templates ----

export async function getTemplates(
  category?: string,
  primaryEntityType?: string
): Promise<TemplateListResponse[]> {
  const params = new URLSearchParams();
  if (category) params.set("category", category);
  if (primaryEntityType) params.set("primaryEntityType", primaryEntityType);
  const qs = params.toString();
  return api.get<TemplateListResponse[]>(`/api/templates${qs ? `?${qs}` : ""}`);
}

export async function getTemplateDetail(id: string): Promise<TemplateDetailResponse> {
  return api.get<TemplateDetailResponse>(`/api/templates/${id}`);
}

export async function createTemplate(req: CreateTemplateRequest): Promise<TemplateDetailResponse> {
  return api.post<TemplateDetailResponse>("/api/templates", req);
}

export async function updateTemplate(
  id: string,
  req: UpdateTemplateRequest
): Promise<TemplateDetailResponse> {
  return api.put<TemplateDetailResponse>(`/api/templates/${id}`, req);
}

export async function deleteTemplate(id: string): Promise<void> {
  return api.delete<void>(`/api/templates/${id}`);
}

export async function cloneTemplate(id: string): Promise<TemplateDetailResponse> {
  return api.post<TemplateDetailResponse>(`/api/templates/${id}/clone`);
}

export async function resetTemplate(id: string): Promise<void> {
  return api.post<void>(`/api/templates/${id}/reset`);
}

export async function previewTemplate(
  id: string,
  entityId: string,
  clauses?: Array<{ clauseId: string; sortOrder: number }>
): Promise<PreviewResponse> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("POST");
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(`${API_BASE}/api/templates/${id}/preview`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authOptions.headers,
    },
    body: JSON.stringify({ entityId, ...(clauses?.length ? { clauses } : {}) }),
    credentials: authOptions.credentials,
  });

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText);
  }

  return response.json() as Promise<PreviewResponse>;
}

// ---- DOCX Template Upload ----

export async function uploadDocxTemplate(
  file: File,
  name: string,
  category: string,
  entityType: string,
  description?: string
): Promise<TemplateDetailResponse> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("POST");
  } catch {
    redirect("/sign-in");
  }

  const formData = new FormData();
  formData.append("file", file);
  formData.append("name", name);
  formData.append("category", category);
  formData.append("entityType", entityType);
  if (description) {
    formData.append("description", description);
  }

  const response = await fetch(`${API_BASE}/api/templates/docx/upload`, {
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

  return response.json() as Promise<TemplateDetailResponse>;
}

export async function replaceDocxFile(
  templateId: string,
  file: File
): Promise<TemplateDetailResponse> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("PUT");
  } catch {
    redirect("/sign-in");
  }

  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE}/api/templates/${templateId}/docx/replace`, {
    method: "PUT",
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

  return response.json() as Promise<TemplateDetailResponse>;
}

export async function getDocxFields(
  templateId: string
): Promise<import("@/lib/types").DiscoveredField[]> {
  return api.get<import("@/lib/types").DiscoveredField[]>(
    `/api/templates/${templateId}/docx/fields`
  );
}

export async function downloadDocxTemplate(templateId: string): Promise<string> {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("GET");
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(`${API_BASE}/api/templates/${templateId}/docx/download`, {
    method: "GET",
    headers: {
      ...authOptions.headers,
    },
    redirect: "manual",
    credentials: authOptions.credentials,
  });

  if (response.status === 302) {
    return response.headers.get("Location") || "";
  }

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

  // Fallback: follow redirect manually
  return response.url;
}
