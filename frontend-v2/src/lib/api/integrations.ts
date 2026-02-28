import "server-only";

import { api } from "@/lib/api";
import type {
  OrgIntegration,
  ConnectionTestResult,
  IntegrationDomain,
  UpsertIntegrationRequest,
  SetApiKeyRequest,
  ToggleIntegrationRequest,
} from "@/lib/types";

// ---- API Functions ----

export async function listIntegrations(): Promise<OrgIntegration[]> {
  return api.get<OrgIntegration[]>("/api/integrations");
}

export async function listProviders(): Promise<Partial<Record<IntegrationDomain, string[]>>> {
  return api.get<Partial<Record<IntegrationDomain, string[]>>>("/api/integrations/providers");
}

export async function upsertIntegration(
  domain: IntegrationDomain,
  data: UpsertIntegrationRequest,
): Promise<OrgIntegration> {
  return api.put<OrgIntegration>(`/api/integrations/${domain}`, data);
}

export async function setApiKey(
  domain: IntegrationDomain,
  data: SetApiKeyRequest,
): Promise<void> {
  return api.post<void>(`/api/integrations/${domain}/set-key`, data);
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
  data: ToggleIntegrationRequest,
): Promise<OrgIntegration> {
  return api.patch<OrgIntegration>(`/api/integrations/${domain}/toggle`, data);
}
