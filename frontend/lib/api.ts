import "server-only";

import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { notFound } from "next/navigation";
import type { ProblemDetail } from "@/lib/types";

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
  const { getToken } = await auth();
  const token = await getToken();

  if (!token) {
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

import type {
  EntityType,
  FieldDefinitionResponse,
  CreateFieldDefinitionRequest,
  UpdateFieldDefinitionRequest,
  FieldGroupResponse,
  CreateFieldGroupRequest,
  UpdateFieldGroupRequest,
  FieldGroupMemberResponse,
} from "@/lib/types";

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
