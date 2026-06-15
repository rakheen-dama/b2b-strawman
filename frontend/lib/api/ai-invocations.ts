import "server-only";

import { api } from "./client";

// ---- Types ----

export type InvocationStatus =
  | "RUNNING"
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "AUTO_APPLIED"
  | "FAILED"
  | "EXPIRED";

export type InvocationSource = "MEMBER" | "AUTOMATION" | "SCHEDULED";

export interface InvocationListItem {
  id: string;
  specialistId: string;
  invokedBy: InvocationSource;
  status: InvocationStatus;
  contextEntityType: string;
  contextEntityId: string;
  createdAt: string;
  proposedOutputSummary: string | null;
  automationActionExecutionId: string | null;
}

export interface InvocationDetail {
  id: string;
  specialistId: string;
  invokedBy: InvocationSource;
  actorId: string | null;
  automationActionExecutionId: string | null;
  contextEntityType: string;
  contextEntityId: string;
  status: InvocationStatus;
  proposedOutput: Record<string, unknown> | null;
  appliedOutput: Record<string, unknown> | null;
  createdAt: string;
  reviewedAt: string | null;
  reviewedById: string | null;
  rejectReason: string | null;
  errorMessage: string | null;
  promptVersion: string | null;
  version: number;
}

export interface InvocationFilter {
  status?: InvocationStatus;
  specialistId?: string;
  from?: string;
  to?: string;
  contextEntityType?: string;
  contextEntityId?: string;
  actorId?: string;
  page?: number;
  size?: number;
}

export interface InvocationPage {
  content: InvocationListItem[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

// ---- API Functions (server-side) ----

export async function listInvocations(filter: InvocationFilter): Promise<InvocationPage> {
  const params = new URLSearchParams();
  if (filter.status) params.set("status", filter.status);
  if (filter.specialistId) params.set("specialistId", filter.specialistId);
  if (filter.from) params.set("from", filter.from);
  if (filter.to) params.set("to", filter.to);
  if (filter.contextEntityType) params.set("contextEntityType", filter.contextEntityType);
  if (filter.contextEntityId) params.set("contextEntityId", filter.contextEntityId);
  if (filter.actorId) params.set("actorId", filter.actorId);
  if (filter.page !== undefined) params.set("page", String(filter.page));
  if (filter.size !== undefined) params.set("size", String(filter.size));
  const qs = params.toString();
  return api.get<InvocationPage>(`/api/assistant/invocations${qs ? `?${qs}` : ""}`);
}

export async function getInvocation(id: string): Promise<InvocationDetail> {
  return api.get<InvocationDetail>(`/api/assistant/invocations/${id}`);
}

export interface ApproveInvocationResult {
  id: string;
  status: string;
  appliedAt: string;
}

export async function approveInvocation(
  id: string,
  appliedOutput?: Record<string, unknown>
): Promise<ApproveInvocationResult> {
  return api.post<ApproveInvocationResult>(
    `/api/assistant/invocations/${id}/approve`,
    appliedOutput ? { appliedOutput } : {}
  );
}

export async function rejectInvocation(id: string, rejectReason: string): Promise<void> {
  await api.post(`/api/assistant/invocations/${id}/reject`, { rejectReason });
}
