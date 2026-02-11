"use server";

import { api, ApiError } from "@/lib/api";
import type {
  MyWorkTasksResponse,
  MyWorkTimeEntryItem,
  MyWorkTimeSummary,
} from "@/lib/types";

export async function fetchMyTasks(
  filter?: string,
  status?: string,
  projectId?: string
): Promise<MyWorkTasksResponse> {
  const params = new URLSearchParams();
  if (filter) params.set("filter", filter);
  if (status) params.set("status", status);
  if (projectId) params.set("projectId", projectId);

  const query = params.toString();
  const url = `/api/my-work/tasks${query ? `?${query}` : ""}`;

  return api.get<MyWorkTasksResponse>(url);
}

export async function fetchMyTimeEntries(
  from?: string,
  to?: string
): Promise<MyWorkTimeEntryItem[]> {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);

  const query = params.toString();
  const url = `/api/my-work/time-entries${query ? `?${query}` : ""}`;

  return api.get<MyWorkTimeEntryItem[]>(url);
}

export async function fetchMyTimeSummary(
  from: string,
  to: string
): Promise<MyWorkTimeSummary | null> {
  const params = new URLSearchParams();
  params.set("from", from);
  params.set("to", to);

  try {
    return await api.get<MyWorkTimeSummary>(
      `/api/my-work/time-summary?${params.toString()}`
    );
  } catch (error) {
    if (error instanceof ApiError) {
      return null;
    }
    return null;
  }
}
