import "server-only";

import { api } from "@/lib/api";
import type {
  OrgIntegration,
  ConnectionTestResult,
  IntegrationDomain,
} from "@/lib/types";

// ---- API Functions ----

export async function listIntegrations(): Promise<OrgIntegration[]> {
  return api.get<OrgIntegration[]>("/api/integrations");
}

export async function listProviders(): Promise<Record<string, string[]>> {
  return api.get<Record<string, string[]>>("/api/integrations/providers");
}

export async function upsertIntegration(
  domain: IntegrationDomain,
  data: { providerSlug: string; configJson?: string },
): Promise<OrgIntegration> {
  return api.put<OrgIntegration>(`/api/integrations/${domain}`, data);
}

export async function setApiKey(
  domain: IntegrationDomain,
  apiKey: string,
): Promise<void> {
  return api.post<void>(`/api/integrations/${domain}/set-key`, { apiKey });
}

export async function testConnection(
  domain: IntegrationDomain,
): Promise<ConnectionTestResult> {
  return api.post<ConnectionTestResult>(`/api/integrations/${domain}/test`);
}

export async function deleteApiKey(
  domain: IntegrationDomain,
): Promise<void> {
  return api.delete<void>(`/api/integrations/${domain}/key`);
}

export async function toggleIntegration(
  domain: IntegrationDomain,
  enabled: boolean,
): Promise<OrgIntegration> {
  return api.patch<OrgIntegration>(`/api/integrations/${domain}/toggle`, { enabled });
}
