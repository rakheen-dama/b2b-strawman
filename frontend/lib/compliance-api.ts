import "server-only";

import { api } from "@/lib/api";
import type {
  LifecycleHistoryEntry,
  TransitionResponse,
  DataRequestResponse,
  AnonymizationResult,
} from "@/lib/types";

export async function transitionLifecycle(
  id: string,
  targetStatus: string,
  notes?: string,
): Promise<TransitionResponse> {
  return api.post<TransitionResponse>(`/api/customers/${id}/transition`, {
    targetStatus,
    notes: notes ?? null,
  });
}

export async function getLifecycleHistory(id: string): Promise<LifecycleHistoryEntry[]> {
  return api.get<LifecycleHistoryEntry[]>(`/api/customers/${id}/lifecycle`);
}

export async function runDormancyCheck(): Promise<{
  thresholdDays: number;
  candidates: Array<{
    customerId: string;
    customerName: string;
    lastActivityDate: string | null;
    daysSinceActivity: number;
  }>;
}> {
  return api.post(`/api/customers/dormancy-check`);
}

export async function getDataRequests(status?: string): Promise<DataRequestResponse[]> {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  return api.get<DataRequestResponse[]>(`/api/data-requests${qs}`);
}

export async function getDataRequest(id: string): Promise<DataRequestResponse> {
  return api.get<DataRequestResponse>(`/api/data-requests/${id}`);
}

export async function createDataRequestApi(body: {
  customerId: string;
  requestType: string;
  description: string;
}): Promise<DataRequestResponse> {
  return api.post<DataRequestResponse>("/api/data-requests", body);
}

export async function updateDataRequestStatus(
  id: string,
  action: string,
  reason?: string,
): Promise<DataRequestResponse> {
  return api.put<DataRequestResponse>(`/api/data-requests/${id}/status`, { action, reason });
}

export async function generateDataExport(id: string): Promise<{ exportFileKey: string }> {
  return api.post<{ exportFileKey: string }>(`/api/data-requests/${id}/export`);
}

export async function getExportDownloadUrl(
  id: string,
): Promise<{ url: string; expiresInSeconds: number }> {
  return api.get<{ url: string; expiresInSeconds: number }>(
    `/api/data-requests/${id}/export/download`,
  );
}

export async function executeDataDeletion(
  id: string,
  confirmCustomerName: string,
): Promise<AnonymizationResult> {
  return api.post<AnonymizationResult>(`/api/data-requests/${id}/execute-deletion`, {
    confirmCustomerName,
  });
}
