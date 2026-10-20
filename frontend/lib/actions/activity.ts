"use server";

import { api } from "@/lib/api";
import type { ActivityResponse } from "@/lib/types";

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
  return api.get<ActivityResponse>(`/api/matters/${projectId}/activity${query ? `?${query}` : ""}`);
}
