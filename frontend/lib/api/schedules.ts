import "server-only";

import { api } from "@/lib/api";

// Shared types/constants re-exported from schedule-constants (no server-only)
export type { ScheduleStatus, RecurrenceFrequency } from "@/lib/schedule-constants";
export { FREQUENCY_LABELS } from "@/lib/schedule-constants";

import type { ScheduleStatus, RecurrenceFrequency } from "@/lib/schedule-constants";

export interface ScheduleResponse {
  id: string;
  templateId: string;
  templateName: string;
  customerId: string;
  customerName: string;
  frequency: RecurrenceFrequency;
  startDate: string;
  endDate: string | null;
  leadTimeDays: number;
  status: ScheduleStatus;
  nextExecutionDate: string | null;
  lastExecutedAt: string | null;
  executionCount: number;
  projectLeadMemberId: string | null;
  projectLeadName: string | null;
  nameOverride: string | null;
  createdBy: string;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateScheduleRequest {
  templateId: string;
  customerId: string;
  frequency: RecurrenceFrequency;
  startDate: string;
  endDate?: string;
  leadTimeDays: number;
  projectLeadMemberId?: string;
  nameOverride?: string;
}

export interface UpdateScheduleRequest {
  nameOverride?: string;
  endDate?: string;
  leadTimeDays: number;
  projectLeadMemberId?: string;
}

export interface ScheduleExecutionResponse {
  id: string;
  projectId: string;
  projectName: string;
  periodStart: string;
  periodEnd: string;
  executedAt: string;
}

export interface ListSchedulesParams {
  status?: ScheduleStatus;
  customerId?: string;
  templateId?: string;
}

// ---- API Functions ----

export async function getSchedules(
  params?: ListSchedulesParams,
): Promise<ScheduleResponse[]> {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.customerId) qs.set("customerId", params.customerId);
  if (params?.templateId) qs.set("templateId", params.templateId);
  const query = qs.toString();
  return api.get<ScheduleResponse[]>(`/api/schedules${query ? `?${query}` : ""}`);
}

export async function getSchedule(id: string): Promise<ScheduleResponse> {
  return api.get<ScheduleResponse>(`/api/schedules/${id}`);
}

export async function createSchedule(
  data: CreateScheduleRequest,
): Promise<ScheduleResponse> {
  return api.post<ScheduleResponse>("/api/schedules", data);
}

export async function updateSchedule(
  id: string,
  data: UpdateScheduleRequest,
): Promise<ScheduleResponse> {
  return api.put<ScheduleResponse>(`/api/schedules/${id}`, data);
}

export async function deleteSchedule(id: string): Promise<void> {
  return api.delete<void>(`/api/schedules/${id}`);
}

export async function pauseSchedule(id: string): Promise<ScheduleResponse> {
  return api.post<ScheduleResponse>(`/api/schedules/${id}/pause`);
}

export async function resumeSchedule(id: string): Promise<ScheduleResponse> {
  return api.post<ScheduleResponse>(`/api/schedules/${id}/resume`);
}

export async function getExecutions(
  scheduleId: string,
): Promise<ScheduleExecutionResponse[]> {
  return api.get<ScheduleExecutionResponse[]>(`/api/schedules/${scheduleId}/executions`);
}
