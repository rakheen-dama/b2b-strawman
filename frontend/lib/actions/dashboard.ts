"use server";

import { api } from "@/lib/api";
import { getAggregatedClientCompleteness } from "@/lib/api/clients";
import type {
  KpiResponse,
  MatterHealth,
  TeamWorkloadEntry,
  CrossMatterActivityItem,
  PersonalDashboardResponse,
} from "@/lib/dashboard-types";
import type { AggregatedCompletenessResponse } from "@/lib/types";
import type { InformationRequestSummary } from "@/lib/api/information-requests";
import { listRules, listExecutions } from "@/lib/api/automations";
import type { AutomationSummary } from "@/lib/api/automations";

export async function fetchDashboardKpis(from: string, to: string): Promise<KpiResponse | null> {
  try {
    return await api.get<KpiResponse>(`/api/dashboard/kpis?from=${from}&to=${to}`);
  } catch {
    return null;
  }
}

export async function fetchProjectHealth(): Promise<MatterHealth[] | null> {
  try {
    return await api.get<MatterHealth[]>("/api/dashboard/matter-health");
  } catch {
    return null;
  }
}

export async function fetchTeamWorkload(
  from: string,
  to: string
): Promise<TeamWorkloadEntry[] | null> {
  try {
    return await api.get<TeamWorkloadEntry[]>(`/api/dashboard/team-workload?from=${from}&to=${to}`);
  } catch {
    return null;
  }
}

export async function fetchDashboardActivity(
  limit: number = 10
): Promise<CrossMatterActivityItem[] | null> {
  try {
    return await api.get<CrossMatterActivityItem[]>(`/api/dashboard/activity?limit=${limit}`);
  } catch {
    return null;
  }
}

export async function fetchAggregatedCompleteness(): Promise<AggregatedCompletenessResponse | null> {
  try {
    return await getAggregatedClientCompleteness();
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

export async function fetchInformationRequestSummary(): Promise<InformationRequestSummary | null> {
  try {
    return await api.get<InformationRequestSummary>("/api/information-requests/summary");
  } catch {
    return null;
  }
}

export async function fetchAutomationSummary(): Promise<AutomationSummary | null> {
  try {
    const [rules, executions] = await Promise.all([
      listRules({ enabled: true }),
      listExecutions({ size: 100 }),
    ]);

    const todayStart = new Date();
    todayStart.setUTCHours(0, 0, 0, 0);

    const todayExecutions = executions.content.filter((e) => new Date(e.startedAt) >= todayStart);

    return {
      activeRulesCount: rules.length,
      todayTotal: todayExecutions.length,
      todaySucceeded: todayExecutions.filter((e) => e.status === "ACTIONS_COMPLETED").length,
      todayFailed: todayExecutions.filter((e) => e.status === "ACTIONS_FAILED").length,
    };
  } catch {
    return null;
  }
}
