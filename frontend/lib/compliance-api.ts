import "server-only";

import { api } from "@/lib/api";
import { listClients } from "@/lib/api/clients";
import type { DataRequestResponse, DataRequestStatus, AnonymizationResult } from "@/lib/types";

// Client lifecycle transition / history / summary and the dormancy check are owned by
// `lib/api/clients.ts` (Phase 87 610C; 610A review H1). This file keeps the data-request
// (DSAR) wrappers and the compliance-dashboard composites.

export async function getDataRequests(status?: DataRequestStatus): Promise<DataRequestResponse[]> {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  return api.get<DataRequestResponse[]>(`/api/data-requests${qs}`);
}

export async function getDataRequest(id: string): Promise<DataRequestResponse> {
  return api.get<DataRequestResponse>(`/api/data-requests/${id}`);
}

export async function createDataRequestApi(body: {
  clientId: string;
  requestType: string;
  description: string;
}): Promise<DataRequestResponse> {
  return api.post<DataRequestResponse>("/api/data-requests", body);
}

export async function updateDataRequestStatus(
  id: string,
  action: string,
  reason?: string
): Promise<DataRequestResponse> {
  return api.put<DataRequestResponse>(`/api/data-requests/${id}/status`, { action, reason });
}

export async function generateDataExport(id: string): Promise<{ exportFileKey: string }> {
  return api.post<{ exportFileKey: string }>(`/api/data-requests/${id}/export`);
}

export async function getExportDownloadUrl(
  id: string
): Promise<{ url: string; expiresInSeconds: number }> {
  return api.get<{ url: string; expiresInSeconds: number }>(
    `/api/data-requests/${id}/export/download`
  );
}

export async function executeDataDeletion(
  id: string,
  confirmCustomerName: string
): Promise<AnonymizationResult> {
  return api.post<AnonymizationResult>(`/api/data-requests/${id}/execute-deletion`, {
    confirmClientName: confirmCustomerName,
  });
}

export async function getOnboardingPipelineData(): Promise<
  Array<{
    id: string;
    name: string;
    lifecycleStatusChangedAt: string | null;
    checklistProgress: { completed: number; total: number };
  }>
> {
  const clients = await listClients({ lifecycleStatus: "ONBOARDING" });
  // Return with stub checklistProgress — progress can't be fetched per-customer in bulk without N+1
  // TODO: Add bulk checklist progress endpoint for efficient dashboard loading
  return clients.map((c) => ({
    id: c.id,
    name: c.name,
    lifecycleStatusChangedAt: c.lifecycleStatusChangedAt ?? null,
    checklistProgress: { completed: 0, total: 0 },
  }));
}

export async function getDashboardDataRequests(): Promise<DataRequestResponse[]> {
  const [received, inProgress] = await Promise.all([
    api.get<DataRequestResponse[]>("/api/data-requests?status=RECEIVED"),
    api.get<DataRequestResponse[]>("/api/data-requests?status=IN_PROGRESS"),
  ]);
  const all = [...received, ...inProgress];
  all.sort((a, b) => (a.deadline < b.deadline ? -1 : 1));
  return all.slice(0, 5);
}
