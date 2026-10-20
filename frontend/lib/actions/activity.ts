"use server";

import { getMatterActivity } from "@/lib/api/matters";
import type { ActivityResponse } from "@/lib/types";

export async function fetchProjectActivity(
  projectId: string,
  entityType?: string,
  page?: number,
  size?: number
): Promise<ActivityResponse> {
  return getMatterActivity(projectId, { entityType, page, size: size ?? 20 });
}
