"use server";

import { api } from "@/lib/api";

export interface ActivityItem {
  id: string;
  message: string;
  actorName: string;
  actorAvatarUrl: string | null;
  entityType: string;
  entityId: string;
  entityName: string;
  eventType: string;
  occurredAt: string;
}

export interface ActivityResponse {
  content: ActivityItem[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export async function fetchProjectActivity(
  projectId: string,
  entityType?: string,
  page?: number,
  size?: number
): Promise<ActivityResponse> {
  const params = new URLSearchParams();
  if (entityType) params.set("entityType", entityType);
  if (page !== undefined) params.set("page", String(page));
  params.set("size", String(size ?? 20));

  const query = params.toString();
  return api.get<ActivityResponse>(
    `/api/projects/${projectId}/activity${query ? `?${query}` : ""}`
  );
}
