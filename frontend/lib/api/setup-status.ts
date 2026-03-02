import "server-only";

import { api } from "@/lib/api";
import type {
  ProjectSetupStatus,
  CustomerReadiness,
  UnbilledTimeSummary,
  TemplateReadiness,
  CompletenessScore,
} from "@/lib/types";

export async function fetchProjectSetupStatus(
  projectId: string,
): Promise<ProjectSetupStatus> {
  return api.get<ProjectSetupStatus>(
    `/api/projects/${projectId}/setup-status`,
  );
}

export async function fetchCustomerReadiness(
  customerId: string,
): Promise<CustomerReadiness> {
  return api.get<CustomerReadiness>(
    `/api/customers/${customerId}/readiness`,
  );
}

export async function fetchProjectUnbilledSummary(
  projectId: string,
): Promise<UnbilledTimeSummary> {
  return api.get<UnbilledTimeSummary>(
    `/api/projects/${projectId}/unbilled-summary`,
  );
}

export async function fetchCustomerUnbilledSummary(
  customerId: string,
): Promise<UnbilledTimeSummary> {
  return api.get<UnbilledTimeSummary>(
    `/api/customers/${customerId}/unbilled-summary`,
  );
}

export async function fetchCompletenessScore(
  customerId: string,
): Promise<CompletenessScore | null> {
  try {
    const params = new URLSearchParams();
    params.append("customerIds", customerId);
    const result = await api.get<Record<string, CompletenessScore>>(
      `/api/customers/completeness-summary?${params.toString()}`,
    );
    return result[customerId] ?? null;
  } catch {
    return null;
  }
}

export async function fetchTemplateReadiness(
  entityType: "PROJECT" | "CUSTOMER",
  entityId: string,
): Promise<TemplateReadiness[]> {
  return api.get<TemplateReadiness[]>(
    `/api/templates/readiness?entityType=${encodeURIComponent(entityType)}&entityId=${encodeURIComponent(entityId)}`,
  );
}
