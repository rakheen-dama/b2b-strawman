"use server";

import { fetchProjectActivity } from "@/lib/actions/activity";
import type { ActivityResponse } from "@/lib/types";

export async function loadMoreActivity(
  projectId: string,
  entityType?: string,
  page?: number
): Promise<ActivityResponse> {
  return fetchProjectActivity(projectId, entityType || undefined, page ?? 0, 20);
}
