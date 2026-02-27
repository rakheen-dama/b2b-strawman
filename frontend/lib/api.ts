import "server-only";

import { getAuthToken } from "@/lib/auth";
import { redirect } from "next/navigation";
import { notFound } from "next/navigation";
import type { ProblemDetail } from "@/lib/types";
import type {
  EntityType,
  FieldDefinitionResponse,
  CreateFieldDefinitionRequest,
  UpdateFieldDefinitionRequest,
  FieldGroupResponse,
  CreateFieldGroupRequest,
  UpdateFieldGroupRequest,
  FieldGroupMemberResponse,
  TagResponse,
  CreateTagRequest,
  UpdateTagRequest,
  SetEntityTagsRequest,
  SavedViewResponse,
  CreateSavedViewRequest,
  UpdateSavedViewRequest,
  TemplateListResponse,
  TemplateDetailResponse,
  CreateTemplateRequest,
  UpdateTemplateRequest,
  OrgSettings,
  UpdateOrgSettingsRequest,
  GenerateDocumentResponse,
  GeneratedDocumentListResponse,
  TemplateEntityType,
  PreviewResponse,
} from "@/lib/types";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public detail?: ProblemDetail
  ) {
    super(message);
    this.name = "ApiError";
  }
}

interface ApiRequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  body?: unknown;
  cache?: RequestCache;
  next?: NextFetchRequestConfig;
  headers?: Record<string, string>;
}

/**
 * Server-side API client for Spring Boot backend.
 * Automatically attaches Clerk JWT as Bearer token.
 * Use only in Server Components, Server Actions, or Route Handlers.
 */
async function apiRequest<T>(endpoint: string, options: ApiRequestOptions = {}): Promise<T> {
  let token: string;
  try {
    token = await getAuthToken();
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(`${BACKEND_URL}${endpoint}`, {
    method: options.method || "GET",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
    cache: options.cache,
    next: options.next,
  });

  if (!response.ok) {
    let detail: ProblemDetail | undefined;
    let message = response.statusText;

    try {
      const contentType = response.headers.get("content-type");
      if (
        contentType?.includes("application/json") ||
        contentType?.includes("application/problem+json")
      ) {
        detail = await response.json();
        message = detail?.detail || detail?.title || message;
      } else {
        message = (await response.text()) || message;
      }
    } catch {
      // Failed to parse error body — use statusText
    }

    throw new ApiError(response.status, message, detail);
  }

  const contentLength = response.headers.get("content-length");
  if (response.status === 204 || contentLength === "0") {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}

/**
 * Handles ApiError by performing appropriate navigation for
 * known HTTP error statuses. Call this in catch blocks of
 * server components and server actions.
 *
 * - 401 → redirect to sign-in
 * - 404 → trigger Next.js not-found page
 * - All others → re-throws the error
 */
export function handleApiError(error: unknown): never {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      redirect("/sign-in");
    }
    if (error.status === 404) {
      notFound();
    }
  }
  throw error;
}

export const api = {
  get: <T>(endpoint: string, options?: Omit<ApiRequestOptions, "method" | "body">) =>
    apiRequest<T>(endpoint, { ...options, method: "GET" }),

  post: <T>(
    endpoint: string,
    body?: unknown,
    options?: Omit<ApiRequestOptions, "method" | "body">
  ) => apiRequest<T>(endpoint, { ...options, method: "POST", body }),

  put: <T>(
    endpoint: string,
    body?: unknown,
    options?: Omit<ApiRequestOptions, "method" | "body">
  ) => apiRequest<T>(endpoint, { ...options, method: "PUT", body }),

  patch: <T>(
    endpoint: string,
    body?: unknown,
    options?: Omit<ApiRequestOptions, "method" | "body">
  ) => apiRequest<T>(endpoint, { ...options, method: "PATCH", body }),

  delete: <T>(endpoint: string, options?: Omit<ApiRequestOptions, "method" | "body">) =>
    apiRequest<T>(endpoint, { ...options, method: "DELETE" }),
};

// Backward-compatible alias for existing code
export { apiRequest as apiClient };

// ---- Field Definitions ----

export async function getFieldDefinitions(
  entityType: string,
): Promise<FieldDefinitionResponse[]> {
  return api.get<FieldDefinitionResponse[]>(
    `/api/field-definitions?entityType=${entityType}`,
  );
}

export async function getFieldDefinition(
  id: string,
): Promise<FieldDefinitionResponse> {
  return api.get<FieldDefinitionResponse>(`/api/field-definitions/${id}`);
}

export async function createFieldDefinition(
  req: CreateFieldDefinitionRequest,
): Promise<FieldDefinitionResponse> {
  return api.post<FieldDefinitionResponse>("/api/field-definitions", req);
}

export async function updateFieldDefinition(
  id: string,
  req: UpdateFieldDefinitionRequest,
): Promise<FieldDefinitionResponse> {
  return api.put<FieldDefinitionResponse>(`/api/field-definitions/${id}`, req);
}

export async function deleteFieldDefinition(id: string): Promise<void> {
  return api.delete<void>(`/api/field-definitions/${id}`);
}

// ---- Field Groups ----

export async function getFieldGroups(
  entityType: string,
): Promise<FieldGroupResponse[]> {
  return api.get<FieldGroupResponse[]>(
    `/api/field-groups?entityType=${entityType}`,
  );
}

export async function getFieldGroup(
  id: string,
): Promise<FieldGroupResponse> {
  return api.get<FieldGroupResponse>(`/api/field-groups/${id}`);
}

export async function createFieldGroup(
  req: CreateFieldGroupRequest,
): Promise<FieldGroupResponse> {
  return api.post<FieldGroupResponse>("/api/field-groups", req);
}

export async function updateFieldGroup(
  id: string,
  req: UpdateFieldGroupRequest,
): Promise<FieldGroupResponse> {
  return api.put<FieldGroupResponse>(`/api/field-groups/${id}`, req);
}

export async function deleteFieldGroup(id: string): Promise<void> {
  return api.delete<void>(`/api/field-groups/${id}`);
}

export async function getGroupMembers(
  groupId: string,
): Promise<FieldGroupMemberResponse[]> {
  return api.get<FieldGroupMemberResponse[]>(
    `/api/field-groups/${groupId}/fields`,
  );
}

// ---- Entity Custom Field Groups ----

export async function setEntityFieldGroups(
  entityType: EntityType,
  entityId: string,
  appliedFieldGroups: string[],
): Promise<FieldDefinitionResponse[]> {
  const prefix = entityType.toLowerCase() + "s";
  return api.put<FieldDefinitionResponse[]>(
    `/api/${prefix}/${entityId}/field-groups`,
    { appliedFieldGroups },
  );
}

// ---- Tags ----

export async function getTags(): Promise<TagResponse[]> {
  return api.get<TagResponse[]>("/api/tags");
}

export async function searchTags(prefix: string): Promise<TagResponse[]> {
  return api.get<TagResponse[]>(
    `/api/tags?search=${encodeURIComponent(prefix)}`,
  );
}

export async function createTag(req: CreateTagRequest): Promise<TagResponse> {
  return api.post<TagResponse>("/api/tags", req);
}

export async function updateTag(
  id: string,
  req: UpdateTagRequest,
): Promise<TagResponse> {
  return api.put<TagResponse>(`/api/tags/${id}`, req);
}

export async function deleteTag(id: string): Promise<void> {
  return api.delete<void>(`/api/tags/${id}`);
}

export async function getEntityTags(
  entityType: EntityType,
  entityId: string,
): Promise<TagResponse[]> {
  const prefix = entityType.toLowerCase() + "s";
  return api.get<TagResponse[]>(`/api/${prefix}/${entityId}/tags`);
}

export async function setEntityTags(
  entityType: EntityType,
  entityId: string,
  req: SetEntityTagsRequest,
): Promise<TagResponse[]> {
  const prefix = entityType.toLowerCase() + "s";
  return api.post<TagResponse[]>(`/api/${prefix}/${entityId}/tags`, req);
}

// ---- Saved Views ----

export async function getViews(
  entityType: EntityType,
): Promise<SavedViewResponse[]> {
  return api.get<SavedViewResponse[]>(
    `/api/views?entityType=${entityType}`,
  );
}

export async function getSavedView(
  id: string,
): Promise<SavedViewResponse> {
  return api.get<SavedViewResponse>(`/api/views/${id}`);
}

export async function createSavedView(
  req: CreateSavedViewRequest,
): Promise<SavedViewResponse> {
  return api.post<SavedViewResponse>("/api/views", req);
}

export async function updateSavedView(
  id: string,
  req: UpdateSavedViewRequest,
): Promise<SavedViewResponse> {
  return api.put<SavedViewResponse>(`/api/views/${id}`, req);
}

export async function deleteSavedView(id: string): Promise<void> {
  return api.delete<void>(`/api/views/${id}`);
}

// ---- Document Templates ----

export async function getTemplates(
  category?: string,
  primaryEntityType?: string,
): Promise<TemplateListResponse[]> {
  const params = new URLSearchParams();
  if (category) params.set("category", category);
  if (primaryEntityType) params.set("primaryEntityType", primaryEntityType);
  const qs = params.toString();
  return api.get<TemplateListResponse[]>(`/api/templates${qs ? `?${qs}` : ""}`);
}

export async function getTemplateDetail(
  id: string,
): Promise<TemplateDetailResponse> {
  return api.get<TemplateDetailResponse>(`/api/templates/${id}`);
}

export async function createTemplate(
  req: CreateTemplateRequest,
): Promise<TemplateDetailResponse> {
  return api.post<TemplateDetailResponse>("/api/templates", req);
}

export async function updateTemplate(
  id: string,
  req: UpdateTemplateRequest,
): Promise<TemplateDetailResponse> {
  return api.put<TemplateDetailResponse>(`/api/templates/${id}`, req);
}

export async function deleteTemplate(id: string): Promise<void> {
  return api.delete<void>(`/api/templates/${id}`);
}

export async function cloneTemplate(
  id: string,
): Promise<TemplateDetailResponse> {
  return api.post<TemplateDetailResponse>(`/api/templates/${id}/clone`);
}

export async function resetTemplate(id: string): Promise<void> {
  return api.post<void>(`/api/templates/${id}/reset`);
}

export async function previewTemplate(
  id: string,
  entityId: string,
  clauses?: Array<{ clauseId: string; sortOrder: number }>,
): Promise<PreviewResponse> {
  let token: string;
  try {
    token = await getAuthToken();
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(`${BACKEND_URL}/api/templates/${id}/preview`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ entityId, clauses: clauses ?? undefined }),
  });

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText);
  }

  return response.json() as Promise<PreviewResponse>;
}

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
  let token: string;
  try {
    token = await getAuthToken();
  } catch {
    redirect("/sign-in");
  }

  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${BACKEND_URL}/api/settings/logo`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: formData,
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

// ---- Generated Documents ----

export async function generateDocument(
  templateId: string,
  entityId: string,
  saveToDocuments: boolean,
  acknowledgeWarnings: boolean = false,
  clauses?: Array<{ clauseId: string; sortOrder: number }>,
): Promise<GenerateDocumentResponse | Blob> {
  let token: string;
  try {
    token = await getAuthToken();
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(
    `${BACKEND_URL}/api/templates/${templateId}/generate`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ entityId, saveToDocuments, acknowledgeWarnings, clauses: clauses ?? undefined }),
    },
  );

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

  if (saveToDocuments) {
    return response.json() as Promise<GenerateDocumentResponse>;
  }

  return response.blob();
}

export async function fetchGeneratedDocuments(
  entityType: TemplateEntityType,
  entityId: string,
): Promise<GeneratedDocumentListResponse[]> {
  return api.get<GeneratedDocumentListResponse[]>(
    `/api/generated-documents?entityType=${entityType}&entityId=${entityId}`,
  );
}

export async function deleteGeneratedDocument(id: string): Promise<void> {
  return api.delete<void>(`/api/generated-documents/${id}`);
}

export async function downloadGeneratedDocument(id: string): Promise<Blob> {
  let token: string;
  try {
    token = await getAuthToken();
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(
    `${BACKEND_URL}/api/generated-documents/${id}/download`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
      redirect: "follow",
    },
  );

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

  return response.blob();
}
