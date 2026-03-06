import "server-only";

import { api } from "@/lib/api";

// ---- Response Types ----

export interface RequestTemplateItemResponse {
  id: string;
  templateId: string;
  name: string;
  description: string | null;
  responseType: "FILE_UPLOAD" | "TEXT_RESPONSE";
  required: boolean;
  fileTypeHints: string | null;
  sortOrder: number;
  createdAt: string;
}

export interface RequestTemplateResponse {
  id: string;
  name: string;
  description: string | null;
  source: "PLATFORM" | "CUSTOM";
  packId: string | null;
  active: boolean;
  items: RequestTemplateItemResponse[];
  createdAt: string;
  updatedAt: string;
}

// ---- Request Types ----

export interface RequestTemplateItemRequest {
  name: string;
  description?: string;
  responseType: "FILE_UPLOAD" | "TEXT_RESPONSE";
  required: boolean;
  fileTypeHints?: string;
  sortOrder: number;
}

export interface CreateRequestTemplateRequest {
  name: string;
  description?: string;
  items?: RequestTemplateItemRequest[];
}

export interface UpdateRequestTemplateRequest {
  name: string;
  description?: string;
  items?: RequestTemplateItemRequest[];
}

// ---- API Functions ----

export async function listRequestTemplates(
  active?: boolean,
): Promise<RequestTemplateResponse[]> {
  const query = active !== undefined ? `?active=${active}` : "";
  return api.get<RequestTemplateResponse[]>(`/api/request-templates${query}`);
}

export async function getRequestTemplate(
  id: string,
): Promise<RequestTemplateResponse> {
  return api.get<RequestTemplateResponse>(`/api/request-templates/${id}`);
}

export async function createRequestTemplate(
  data: CreateRequestTemplateRequest,
): Promise<RequestTemplateResponse> {
  return api.post<RequestTemplateResponse>("/api/request-templates", data);
}

export async function updateRequestTemplate(
  id: string,
  data: UpdateRequestTemplateRequest,
): Promise<RequestTemplateResponse> {
  return api.put<RequestTemplateResponse>(`/api/request-templates/${id}`, data);
}

export async function deactivateRequestTemplate(id: string): Promise<void> {
  return api.delete<void>(`/api/request-templates/${id}`);
}

export async function duplicateRequestTemplate(
  id: string,
): Promise<RequestTemplateResponse> {
  return api.post<RequestTemplateResponse>(
    `/api/request-templates/${id}/duplicate`,
  );
}
