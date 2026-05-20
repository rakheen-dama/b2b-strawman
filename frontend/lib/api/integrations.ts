import "server-only";

import { api } from "./client";
import type {
  OrgIntegration,
  ConnectionTestResult,
  IntegrationDomain,
  UpsertIntegrationRequest,
  SetApiKeyRequest,
  ToggleIntegrationRequest,
  ModelInfo,
  XeroConnectResponse,
  XeroConnectionResponse,
  XeroTaxMapping,
  UpdateXeroTaxMappingRequest,
  XeroTaxRate,
  XeroCustomerImportResult,
  XeroSettingsResponse,
  UpdateXeroSettingsRequest,
  SyncSummaryResponse,
  SyncEntryResponse,
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
  data: UpsertIntegrationRequest
): Promise<OrgIntegration> {
  return api.put<OrgIntegration>(`/api/integrations/${domain}`, data);
}

export async function setApiKey(domain: IntegrationDomain, data: SetApiKeyRequest): Promise<void> {
  return api.post<void>(`/api/integrations/${domain}/set-key`, data);
}

export async function testConnection(domain: IntegrationDomain): Promise<ConnectionTestResult> {
  return api.post<ConnectionTestResult>(`/api/integrations/${domain}/test`);
}

export async function deleteApiKey(domain: IntegrationDomain): Promise<void> {
  return api.delete<void>(`/api/integrations/${domain}/key`);
}

export async function toggleIntegration(
  domain: IntegrationDomain,
  data: ToggleIntegrationRequest
): Promise<OrgIntegration> {
  return api.patch<OrgIntegration>(`/api/integrations/${domain}/toggle`, data);
}

export async function getAiModels(): Promise<{ models: ModelInfo[] }> {
  return api.get<{ models: ModelInfo[] }>("/api/settings/integrations/ai/models");
}

// ---- Xero Connection ----

export async function initiateXeroConnect(): Promise<XeroConnectResponse> {
  return api.get<XeroConnectResponse>("/api/integrations/xero/connect");
}

export async function getXeroConnection(): Promise<XeroConnectionResponse> {
  return api.get<XeroConnectionResponse>("/api/integrations/xero/connection");
}

export async function disconnectXero(): Promise<void> {
  return api.delete<void>("/api/integrations/xero/connection");
}

// ---- Tax Mappings ----

export async function getXeroTaxMappings(): Promise<XeroTaxMapping[]> {
  return api.get<XeroTaxMapping[]>("/api/integrations/xero/tax-mappings");
}

export async function updateXeroTaxMapping(
  id: string,
  data: UpdateXeroTaxMappingRequest
): Promise<XeroTaxMapping> {
  return api.put<XeroTaxMapping>(`/api/integrations/xero/tax-mappings/${id}`, data);
}

export async function resetXeroTaxMappings(): Promise<void> {
  return api.post<void>("/api/integrations/xero/tax-mappings/reset");
}

export async function getXeroTaxRates(): Promise<XeroTaxRate[]> {
  return api.get<XeroTaxRate[]>("/api/integrations/xero/tax-rates");
}

// ---- Customer Import ----

export async function importXeroCustomers(): Promise<XeroCustomerImportResult> {
  return api.post<XeroCustomerImportResult>("/api/integrations/xero/import-customers");
}

// ---- Xero Settings ----

export async function getXeroSettings(): Promise<XeroSettingsResponse> {
  return api.get<XeroSettingsResponse>("/api/integrations/xero/settings");
}

export async function updateXeroSettings(
  data: UpdateXeroSettingsRequest
): Promise<XeroSettingsResponse> {
  return api.put<XeroSettingsResponse>("/api/integrations/xero/settings", data);
}

// ---- Sync ----

export async function getSyncSummary(): Promise<SyncSummaryResponse> {
  return api.get<SyncSummaryResponse>("/api/integrations/sync/summary");
}

export async function getSyncEntries(params?: {
  state?: string;
  entityType?: string;
  direction?: string;
  page?: number;
  size?: number;
}): Promise<{
  content: SyncEntryResponse[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}> {
  const searchParams = new URLSearchParams();
  if (params?.state) searchParams.set("state", params.state);
  if (params?.entityType) searchParams.set("entityType", params.entityType);
  if (params?.direction) searchParams.set("direction", params.direction);
  if (params?.page !== undefined) searchParams.set("page", String(params.page));
  if (params?.size !== undefined) searchParams.set("size", String(params.size));
  const qs = searchParams.toString();
  return api.get(`/api/integrations/sync/entries${qs ? `?${qs}` : ""}`);
}

export async function getSyncEntry(id: string): Promise<SyncEntryResponse> {
  return api.get<SyncEntryResponse>(`/api/integrations/sync/entries/${id}`);
}

export async function getInvoiceSyncStatus(invoiceId: string): Promise<SyncEntryResponse> {
  return api.get<SyncEntryResponse>(`/api/integrations/sync/invoice/${invoiceId}/status`);
}

export async function retrySyncEntry(id: string): Promise<void> {
  return api.post<void>(`/api/integrations/sync/${id}/retry`);
}

export async function forceResyncInvoice(invoiceId: string): Promise<void> {
  return api.post<void>(`/api/integrations/sync/invoice/${invoiceId}/resync`);
}

export async function reconcileSyncEntry(id: string): Promise<void> {
  return api.post<void>(`/api/integrations/sync/${id}/reconcile`);
}
