"use server";

import { api } from "@/lib/api";
import type {
  KpiResponse,
  ProjectHealth,
  ProjectHealthDetail,
  TaskSummaryResponse,
  MemberHoursEntry,
  TeamWorkloadEntry,
  CrossProjectActivityItem,
  PersonalDashboardResponse,
} from "@/lib/dashboard-types";
import type { AggregatedCompletenessResponse } from "@/lib/types";

export async function fetchDashboardKpis(
  from: string,
  to: string
): Promise<KpiResponse | null> {
  try {
    return await api.get<KpiResponse>(
      `/api/dashboard/kpis?from=${from}&to=${to}`
    );
  } catch {
    return null;
  }
}

export async function fetchProjectHealth(): Promise<ProjectHealth[] | null> {
  try {
    return await api.get<ProjectHealth[]>("/api/dashboard/project-health");
  } catch {
    return null;
  }
}

export async function fetchTeamWorkload(
  from: string,
  to: string
): Promise<TeamWorkloadEntry[] | null> {
  try {
    return await api.get<TeamWorkloadEntry[]>(
      `/api/dashboard/team-workload?from=${from}&to=${to}`
    );
  } catch {
    return null;
  }
}

export async function fetchDashboardActivity(
  limit: number = 10
): Promise<CrossProjectActivityItem[] | null> {
  try {
    return await api.get<CrossProjectActivityItem[]>(
      `/api/dashboard/activity?limit=${limit}`
    );
  } catch {
    return null;
  }
}

export async function fetchAggregatedCompleteness(): Promise<AggregatedCompletenessResponse | null> {
  try {
    return await api.get<AggregatedCompletenessResponse>(
      "/api/customers/completeness-summary/aggregated"
    );
  } catch {
    return null;
  }
}

export async function fetchPersonalDashboard(
  from: string,
  to: string
): Promise<PersonalDashboardResponse | null> {
  try {
    return await api.get<PersonalDashboardResponse>(
      `/api/dashboard/personal?from=${from}&to=${to}`
    );
  } catch {
    return null;
  }
}

// --- Project-scoped fetchers (for project overview tab) ---

export async function fetchProjectHealthDetail(
  projectId: string
): Promise<ProjectHealthDetail | null> {
  try {
    return await api.get<ProjectHealthDetail>(
      `/api/projects/${projectId}/health`
    );
  } catch {
    return null;
  }
}

export async function fetchProjectTaskSummary(
  projectId: string
): Promise<TaskSummaryResponse | null> {
  try {
    return await api.get<TaskSummaryResponse>(
      `/api/projects/${projectId}/task-summary`
    );
  } catch {
    return null;
  }
}

export async function fetchProjectMemberHours(
  projectId: string,
  from: string,
  to: string
): Promise<MemberHoursEntry[] | null> {
  try {
    return await api.get<MemberHoursEntry[]>(
      `/api/projects/${projectId}/member-hours?from=${from}&to=${to}`
    );
  } catch {
    return null;
  }
}
