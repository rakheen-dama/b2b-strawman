import "server-only";

import { api } from "./client";
import type {
  EntityType,
  SavedViewResponse,
  CreateSavedViewRequest,
  UpdateSavedViewRequest,
} from "@/lib/types";

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
