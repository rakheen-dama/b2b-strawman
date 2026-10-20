import "server-only";

import { api } from "./client";
import type {
  MatterSetupStatus,
  ClientReadiness,
  UnbilledTimeSummary,
  TemplateReadiness,
  CompletenessScore,
} from "@/lib/types";

export async function fetchProjectSetupStatus(projectId: string): Promise<MatterSetupStatus> {
  return api.get<MatterSetupStatus>(`/api/matters/${projectId}/setup-status`);
}

export async function fetchCustomerReadiness(customerId: string): Promise<ClientReadiness> {
  return api.get<ClientReadiness>(`/api/clients/${customerId}/readiness`);
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
