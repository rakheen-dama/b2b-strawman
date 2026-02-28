"use server";

import { api, ApiError } from "@/lib/api";
import type {
  ProjectTimeSummary,
  MemberTimeSummary,
  TaskTimeSummary,
} from "@/lib/types";

function buildDateParams(from?: string, to?: string): string {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const query = params.toString();
  return query ? `?${query}` : "";
}

export async function fetchProjectTimeSummary(
  projectId: string,
  from?: string,
  to?: string
): Promise<ProjectTimeSummary> {
  return api.get<ProjectTimeSummary>(
    `/api/projects/${projectId}/time-summary${buildDateParams(from, to)}`
  );
}

export async function fetchTimeSummaryByMember(
  projectId: string,
  from?: string,
  to?: string
): Promise<MemberTimeSummary[] | null> {
  try {
    return await api.get<MemberTimeSummary[]>(
      `/api/projects/${projectId}/time-summary/by-member${buildDateParams(from, to)}`
    );
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
  return api.get<TaskTimeSummary[]>(
    `/api/projects/${projectId}/time-summary/by-task${buildDateParams(from, to)}`
  );
}
