"use server";

import { ApiError } from "@/lib/api";
import {
  getMatterTimeSummary,
  getMatterTimeSummaryByMember,
  getMatterTimeSummaryByTask,
} from "@/lib/api/matters";
import type { MatterTimeSummary, MemberTimeSummary, TaskTimeSummary } from "@/lib/types";

export async function fetchProjectTimeSummary(
  projectId: string,
  from?: string,
  to?: string
): Promise<MatterTimeSummary> {
  return getMatterTimeSummary(projectId, { from, to });
}

export async function fetchTimeSummaryByMember(
  projectId: string,
  from?: string,
  to?: string
): Promise<MemberTimeSummary[] | null> {
  try {
    return await getMatterTimeSummaryByMember(projectId, { from, to });
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return null;
    }
    throw error;
  }
}

export async function fetchTimeSummaryByTask(
  projectId: string,
  from?: string,
  to?: string
): Promise<TaskTimeSummary[]> {
  return getMatterTimeSummaryByTask(projectId, { from, to });
}
