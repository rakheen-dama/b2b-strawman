import "server-only";

import { api } from "./client";
import type {
  EntityType,
  TagResponse,
  CreateTagRequest,
  UpdateTagRequest,
  SetEntityTagsRequest,
} from "@/lib/types";

/**
 * Backend collection segment for an entity's tag / field-group / custom-field endpoints.
 * Phase 87 S1 (ADR-337): matters are served at `/api/matters/**`; the `EntityType` literal
 * itself stays `"PROJECT"` because it is a storage key (ADR-342). Route segments
 * (`/org/{slug}/projects/...`) are NOT derived from this — they move in 610D.
 */
export function entityApiPrefix(entityType: EntityType): string {
  return entityType === "PROJECT" ? "matters" : entityType.toLowerCase() + "s";
}

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
  const prefix = entityApiPrefix(entityType);
  return api.get<TagResponse[]>(`/api/${prefix}/${entityId}/tags`);
}

export async function setEntityTags(
  entityType: EntityType,
  entityId: string,
  req: SetEntityTagsRequest
): Promise<TagResponse[]> {
  const prefix = entityApiPrefix(entityType);
  return api.post<TagResponse[]>(`/api/${prefix}/${entityId}/tags`, req);
}
