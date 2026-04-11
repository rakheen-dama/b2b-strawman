"use server";

import { api } from "@/lib/api";
import type { InterestRun, InterestAllocation, LpffRate } from "@/lib/types/trust";
import type { CreateInterestRunFormData, AddLpffRateFormData } from "@/lib/schemas/trust";

// ── Interest Runs ───────────────────────────────────────────────────

export async function fetchInterestRuns(accountId: string): Promise<InterestRun[]> {
  return api.get<InterestRun[]>(`/api/trust-accounts/${accountId}/interest-runs`);
}

export async function createInterestRun(
  accountId: string,
  data: CreateInterestRunFormData
): Promise<{ success: boolean; run?: InterestRun; error?: string }> {
  try {
    const run = await api.post<InterestRun>(`/api/trust-accounts/${accountId}/interest-runs`, data);
    return { success: true, run };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to create interest run";
    return { success: false, error: message };
  }
}

export async function calculateInterest(
  runId: string
): Promise<{ success: boolean; run?: InterestRun; error?: string }> {
  try {
    const run = await api.post<InterestRun>(`/api/interest-runs/${runId}/calculate`, {});
    return { success: true, run };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to calculate interest";
    return { success: false, error: message };
  }
}

export async function approveInterestRun(
  runId: string
): Promise<{ success: boolean; run?: InterestRun; error?: string }> {
  try {
    const run = await api.post<InterestRun>(`/api/interest-runs/${runId}/approve`, {});
    return { success: true, run };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to approve interest run";
    return { success: false, error: message };
  }
}

export async function postInterestRun(
  runId: string
): Promise<{ success: boolean; run?: InterestRun; error?: string }> {
  try {
    const run = await api.post<InterestRun>(`/api/interest-runs/${runId}/post`, {});
    return { success: true, run };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to post interest run";
    return { success: false, error: message };
  }
}

export async function fetchInterestRunDetail(
  runId: string
): Promise<{ run: InterestRun; allocations: InterestAllocation[] }> {
  return api.get<{ run: InterestRun; allocations: InterestAllocation[] }>(
    `/api/interest-runs/${runId}`
  );
}

// ── LPFF Rates ──────────────────────────────────────────────────────

export async function fetchLpffRates(accountId: string): Promise<LpffRate[]> {
  return api.get<LpffRate[]>(`/api/trust-accounts/${accountId}/lpff-rates`);
}

export async function addLpffRate(
  accountId: string,
  data: AddLpffRateFormData
): Promise<{ success: boolean; rate?: LpffRate; error?: string }> {
  try {
    // Backend stores rates as decimal fractions (e.g. 0.085 for 8.5%),
    // but the form collects user-facing percentages — normalize before sending.
    const payload = {
      ...data,
      ratePercent: data.ratePercent / 100,
      lpffSharePercent: data.lpffSharePercent / 100,
    };
    const rate = await api.post<LpffRate>(`/api/trust-accounts/${accountId}/lpff-rates`, payload);
    return { success: true, rate };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to add LPFF rate";
    return { success: false, error: message };
  }
}
