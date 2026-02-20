import "server-only";

import { api } from "@/lib/api";

// ---- Enums / Union Types ----

export type RetainerStatus = "ACTIVE" | "PAUSED" | "TERMINATED";
// RetainerType is also exported from @/lib/types for client component use
export type RetainerType = "HOUR_BANK" | "FIXED_FEE";
export type RetainerFrequency =
  | "WEEKLY"
  | "FORTNIGHTLY"
  | "MONTHLY"
  | "QUARTERLY"
  | "SEMI_ANNUALLY"
  | "ANNUALLY";
export type RolloverPolicy = "FORFEIT" | "CARRY_FORWARD" | "CARRY_CAPPED";
export type PeriodStatus = "OPEN" | "CLOSED";

// Re-export shared constants so server-side consumers don't need a separate import
export { FREQUENCY_LABELS } from "@/lib/retainer-constants";

// ---- Response Interfaces ----

export interface PeriodSummary {
  id: string;
  periodStart: string;
  periodEnd: string;
  status: PeriodStatus;
  allocatedHours: number;
  baseAllocatedHours: number;
  consumedHours: number;
  remainingHours: number;
  rolloverHoursIn: number;
  overageHours: number;
  rolloverHoursOut: number;
  invoiceId: string | null;
  closedAt: string | null;
  closedBy: string | null;
  readyToClose: boolean;
}

export interface RetainerResponse {
  id: string;
  customerId: string;
  scheduleId: string | null;
  customerName: string;
  name: string;
  type: RetainerType;
  status: RetainerStatus;
  frequency: RetainerFrequency;
  startDate: string;
  endDate: string | null;
  allocatedHours: number | null;
  periodFee: number | null;
  rolloverPolicy: RolloverPolicy | null;
  rolloverCapHours: number | null;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  currentPeriod: PeriodSummary | null;
  recentPeriods: PeriodSummary[];
}

// RetainerSummaryResponse is also exported from @/lib/types for client component use
export interface RetainerSummaryResponse {
  hasActiveRetainer: boolean;
  agreementId: string | null;
  agreementName: string | null;
  type: RetainerType | null;
  allocatedHours: number | null;
  consumedHours: number | null;
  remainingHours: number | null;
  percentConsumed: number | null;
  isOverage: boolean;
}

export interface PeriodCloseResult {
  closedPeriod: PeriodSummary;
  generatedInvoice: {
    id: string;
    invoiceNumber: string | null;
    status: string;
    currency: string;
    total: number;
    lines: Array<{
      description: string;
      quantity: number;
      unitPrice: number;
      amount: number;
    }>;
  } | null;
  nextPeriod: PeriodSummary | null;
}

export interface PaginatedPeriods {
  content: PeriodSummary[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

// ---- Request Interfaces ----

export interface CreateRetainerRequest {
  customerId: string;
  scheduleId?: string;
  name: string;
  type: RetainerType;
  frequency: RetainerFrequency;
  startDate: string;
  endDate?: string;
  allocatedHours?: number;
  periodFee?: number;
  rolloverPolicy?: RolloverPolicy;
  rolloverCapHours?: number;
  notes?: string;
}

export interface UpdateRetainerRequest {
  name: string;
  allocatedHours?: number;
  periodFee?: number;
  rolloverPolicy?: RolloverPolicy;
  rolloverCapHours?: number;
  endDate?: string;
  notes?: string;
}

// ---- List Params ----

export interface ListRetainersParams {
  status?: RetainerStatus;
  customerId?: string;
}

// ---- API Functions ----

export async function fetchRetainers(
  params?: ListRetainersParams,
): Promise<RetainerResponse[]> {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.customerId) qs.set("customerId", params.customerId);
  const query = qs.toString();
  return api.get<RetainerResponse[]>(`/api/retainers${query ? `?${query}` : ""}`);
}

export async function fetchRetainer(id: string): Promise<RetainerResponse> {
  return api.get<RetainerResponse>(`/api/retainers/${id}`);
}

export async function createRetainer(
  data: CreateRetainerRequest,
): Promise<RetainerResponse> {
  return api.post<RetainerResponse>("/api/retainers", data);
}

export async function updateRetainer(
  id: string,
  data: UpdateRetainerRequest,
): Promise<RetainerResponse> {
  return api.put<RetainerResponse>(`/api/retainers/${id}`, data);
}

export async function pauseRetainer(id: string): Promise<RetainerResponse> {
  return api.post<RetainerResponse>(`/api/retainers/${id}/pause`);
}

export async function resumeRetainer(id: string): Promise<RetainerResponse> {
  return api.post<RetainerResponse>(`/api/retainers/${id}/resume`);
}

export async function terminateRetainer(id: string): Promise<RetainerResponse> {
  return api.post<RetainerResponse>(`/api/retainers/${id}/terminate`);
}

export async function fetchPeriods(
  retainerId: string,
  page = 0,
): Promise<PaginatedPeriods> {
  return api.get<PaginatedPeriods>(
    `/api/retainers/${retainerId}/periods?page=${page}&size=20`,
  );
}

export async function fetchCurrentPeriod(retainerId: string): Promise<PeriodSummary> {
  return api.get<PeriodSummary>(`/api/retainers/${retainerId}/periods/current`);
}

export async function closePeriod(retainerId: string): Promise<PeriodCloseResult> {
  return api.post<PeriodCloseResult>(
    `/api/retainers/${retainerId}/periods/current/close`,
  );
}

export async function fetchRetainerSummary(
  customerId: string,
): Promise<RetainerSummaryResponse> {
  return api.get<RetainerSummaryResponse>(
    `/api/customers/${customerId}/retainer-summary`,
  );
}
