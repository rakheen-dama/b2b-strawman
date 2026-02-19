import "server-only";

import { api } from "@/lib/api";
import type { TransitionResponse } from "@/lib/types";

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

export async function getLifecycleHistory(id: string): Promise<unknown[]> {
  return api.get<unknown[]>(`/api/customers/${id}/lifecycle`);
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
