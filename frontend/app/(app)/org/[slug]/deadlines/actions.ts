"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CalculatedDeadline,
  DeadlineSummary,
  FilingStatusRequest,
  DeadlineFiltersType,
} from "@/lib/types";

interface DeadlinesResponse {
  deadlines: CalculatedDeadline[];
  total: number;
}

interface DeadlineSummaryResponse {
  summaries: DeadlineSummary[];
}

interface FilingStatusResult {
  id: string;
  customerId: string;
  deadlineTypeSlug: string;
  periodKey: string;
  status: string;
  filedAt: string | null;
  filedBy: string | null;
  notes: string | null;
  linkedProjectId: string | null;
  createdAt: string;
}

interface BatchFilingStatusResponse {
  results: FilingStatusResult[];
}

export async function fetchDeadlines(
  from: string,
  to: string,
  filters?: Partial<DeadlineFiltersType>
): Promise<DeadlinesResponse> {
  const params = new URLSearchParams();
  params.set("from", from);
  params.set("to", to);
  if (filters?.category) params.set("category", filters.category);
  if (filters?.status) params.set("status", filters.status);
  if (filters?.customerId) params.set("customerId", filters.customerId);

  return api.get<DeadlinesResponse>(`/api/deadlines?${params.toString()}`);
}

export async function fetchDeadlineSummary(
  from: string,
  to: string,
  filters?: Partial<DeadlineFiltersType>
): Promise<DeadlineSummaryResponse> {
  const params = new URLSearchParams();
  params.set("from", from);
  params.set("to", to);
  if (filters?.category) params.set("category", filters.category);
  if (filters?.status) params.set("status", filters.status);

  return api.get<DeadlineSummaryResponse>(
    `/api/deadlines/summary?${params.toString()}`
  );
}

export async function fetchCustomerDeadlines(
  customerId: string,
  from: string,
  to: string
): Promise<DeadlinesResponse> {
  const params = new URLSearchParams();
  params.set("from", from);
  params.set("to", to);
  return api.get<DeadlinesResponse>(
    `/api/customers/${customerId}/deadlines?${params.toString()}`
  );
}

interface UpdateFilingStatusResult {
  success: boolean;
  results?: FilingStatusResult[];
  error?: string;
}

export async function updateFilingStatus(
  slug: string,
  items: FilingStatusRequest[]
): Promise<UpdateFilingStatusResult> {
  try {
    const response = await api.put<BatchFilingStatusResponse>(
      "/api/deadlines/filing-status",
      { items }
    );
    revalidatePath(`/org/${slug}/deadlines`);
    return { success: true, results: response.results };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to update filing status";
    return { success: false, error: message };
  }
}
