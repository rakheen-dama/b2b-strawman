import "server-only";

import { api } from "./client";
import type {
  EntityType,
  TagResponse,
  CreateTagRequest,
  UpdateTagRequest,
  SetEntityTagsRequest,
} from "@/lib/types";

// ---- Tags ----

export async function getTags(): Promise<TagResponse[]> {
  return api.get<TagResponse[]>("/api/tags");
}

export async function searchTags(prefix: string): Promise<TagResponse[]> {
  return api.get<TagResponse[]>(`/api/tags?search=${encodeURIComponent(prefix)}`);
}

export async function createTag(req: CreateTagRequest): Promise<TagResponse> {
  return api.post<TagResponse>("/api/tags", req);
}

export async function updateTag(id: string, req: UpdateTagRequest): Promise<TagResponse> {
  return api.put<TagResponse>(`/api/tags/${id}`, req);
}

export async function deleteTag(id: string): Promise<void> {
  return api.delete<void>(`/api/tags/${id}`);
}

export async function getEntityTags(
  entityType: EntityType,
  entityId: string
): Promise<TagResponse[]> {
  const prefix = entityType.toLowerCase() + "s";
  return api.get<TagResponse[]>(`/api/${prefix}/${entityId}/tags`);
}

export async function setEntityTags(
  entityType: EntityType,
  entityId: string,
  req: SetEntityTagsRequest
): Promise<TagResponse[]> {
  const prefix = entityType.toLowerCase() + "s";
  return api.post<TagResponse[]>(`/api/${prefix}/${entityId}/tags`, req);
}
