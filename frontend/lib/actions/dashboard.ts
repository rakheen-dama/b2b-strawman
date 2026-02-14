"use server";

import { api } from "@/lib/api";
import type {
  KpiResponse,
  ProjectHealth,
  TeamWorkloadEntry,
  CrossProjectActivityItem,
  PersonalDashboardResponse,
} from "@/lib/dashboard-types";

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
