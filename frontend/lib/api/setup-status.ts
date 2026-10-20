import "server-only";

import { api } from "./client";
import type {
  ProjectSetupStatus,
  CustomerReadiness,
  UnbilledTimeSummary,
  TemplateReadiness,
  CompletenessScore,
} from "@/lib/types";

export async function fetchProjectSetupStatus(projectId: string): Promise<ProjectSetupStatus> {
  return api.get<ProjectSetupStatus>(`/api/matters/${projectId}/setup-status`);
}

export async function fetchCustomerReadiness(customerId: string): Promise<CustomerReadiness> {
  return api.get<CustomerReadiness>(`/api/clients/${customerId}/readiness`);
}

export async function fetchProjectUnbilledSummary(projectId: string): Promise<UnbilledTimeSummary> {
  return api.get<UnbilledTimeSummary>(`/api/matters/${projectId}/unbilled-summary`);
}

export async function fetchCustomerUnbilledSummary(
  customerId: string
): Promise<UnbilledTimeSummary> {
  return api.get<UnbilledTimeSummary>(`/api/clients/${customerId}/unbilled-summary`);
}

export async function fetchCompletenessScore(
  customerId: string
): Promise<CompletenessScore | null> {
  try {
    const params = new URLSearchParams();
    params.append("clientIds", customerId);
    const result = await api.get<Record<string, CompletenessScore>>(
      `/api/clients/completeness-summary?${params.toString()}`
    );
    return result[customerId] ?? null;
  } catch {
    return null;
  }
}

export async function fetchTemplateReadiness(
  entityType: "PROJECT" | "CUSTOMER",
  entityId: string
): Promise<TemplateReadiness[]> {
  return api.get<TemplateReadiness[]>(
    `/api/templates/readiness?entityType=${encodeURIComponent(entityType)}&entityId=${encodeURIComponent(entityId)}`
  );
}
