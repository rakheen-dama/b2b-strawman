"use server";

import { revalidatePath } from "next/cache";
import { api } from "@/lib/api";
import type {
  InterestRun,
  InterestRunDetail,
  LpffRate,
} from "@/lib/types";
import type {
  CreateInterestRunFormData,
  AddLpffRateFormData,
} from "@/lib/schemas/trust";

// ── Response types ─────────────────────────────────────────────────

interface ActionResult {
  success: boolean;
  error?: string;
}

interface InterestRunActionResult extends ActionResult {
  data?: InterestRun;
}

interface InterestRunDetailResult extends ActionResult {
  data?: InterestRunDetail;
}

// ── Interest Run actions ──────────────────────────────────────────

export async function fetchInterestRuns(
  accountId: string,
): Promise<InterestRun[]> {
  return api.get<InterestRun[]>(
    `/api/trust-accounts/${accountId}/interest-runs`,
  );
}

export async function createInterestRun(
  accountId: string,
  data: CreateInterestRunFormData,
): Promise<InterestRunActionResult> {
  try {
    const run = await api.post<InterestRun>(
      `/api/trust-accounts/${accountId}/interest-runs`,
      {
        periodStart: data.periodStart,
        periodEnd: data.periodEnd,
      },
    );
    revalidatePath("/", "layout");
    return { success: true, data: run };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to create interest run",
    };
  }
}

export async function fetchInterestRunDetail(
  runId: string,
): Promise<InterestRunDetailResult> {
  try {
    const detail = await api.get<InterestRunDetail>(
      `/api/interest-runs/${runId}`,
    );
    return { success: true, data: detail };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to fetch interest run detail",
    };
  }
}

export async function calculateInterest(
  runId: string,
): Promise<InterestRunActionResult> {
  try {
    const run = await api.post<InterestRun>(
      `/api/interest-runs/${runId}/calculate`,
    );
    revalidatePath("/", "layout");
    return { success: true, data: run };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to calculate interest",
    };
  }
}

export async function approveInterestRun(
  runId: string,
): Promise<InterestRunActionResult> {
  try {
    const run = await api.post<InterestRun>(
      `/api/interest-runs/${runId}/approve`,
    );
    revalidatePath("/", "layout");
    return { success: true, data: run };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to approve interest run",
    };
  }
}

export async function postInterestRun(
  runId: string,
): Promise<InterestRunActionResult> {
  try {
    const run = await api.post<InterestRun>(
      `/api/interest-runs/${runId}/post`,
    );
    revalidatePath("/", "layout");
    return { success: true, data: run };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to post interest run",
    };
  }
}

// ── LPFF Rate actions ─────────────────────────────────────────────

export async function fetchLpffRates(
  accountId: string,
): Promise<LpffRate[]> {
  return api.get<LpffRate[]>(
    `/api/trust-accounts/${accountId}/lpff-rates`,
  );
}

export async function addLpffRate(
  accountId: string,
  data: AddLpffRateFormData,
): Promise<ActionResult> {
  try {
    await api.post(`/api/trust-accounts/${accountId}/lpff-rates`, {
      effectiveFrom: data.effectiveFrom,
      ratePercent: data.ratePercent,
      lpffSharePercent: data.lpffSharePercent,
      notes: data.notes || null,
    });
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error ? error.message : "Failed to add LPFF rate",
    };
  }
}
