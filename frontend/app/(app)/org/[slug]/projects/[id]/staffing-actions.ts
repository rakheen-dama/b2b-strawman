"use server";

import { getAuthContext } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import {
  getProjectStaffing,
  type ProjectStaffingResponse,
} from "@/lib/api/capacity";

interface ActionResult<T> {
  data: T | null;
  error?: string;
}

export async function getProjectStaffingAction(
  projectId: string,
  weekStart: string,
  weekEnd: string,
): Promise<ActionResult<ProjectStaffingResponse>> {
  // Verify auth (any authenticated user can view staffing)
  await getAuthContext();

  try {
    const data = await getProjectStaffing(projectId, weekStart, weekEnd);
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}
