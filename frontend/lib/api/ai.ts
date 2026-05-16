import "server-only";

import { api } from "./client";

// ---- Types ----

export interface AiProfileResponse {
  id: string;
  practiceAreas: string[];
  jurisdiction: string;
  riskCalibration: string;
  houseStyleNotes: string | null;
  ficaRequirements: Record<string, unknown> | null;
  feeEstimationNotes: string | null;
  preferredModel: string;
  monthlyBudgetCents: number | null;
  profileVersion: number;
  coldStartCompleted: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateAiProfileRequest {
  practiceAreas: string[];
  jurisdiction: string;
  riskCalibration: string;
  houseStyleNotes?: string | null;
  ficaRequirements?: Record<string, unknown> | null;
  feeEstimationNotes?: string | null;
  preferredModel: string;
  monthlyBudgetCents?: number | null;
  coldStartCompleted?: boolean;
}

export interface AiCostSummaryResponse {
  currentMonthSpentCents: number;
  monthlyBudgetCents: number | null;
  invocationCount: number;
  remainingBudgetCents: number | null;
  periodStart: string;
  periodEnd: string;
}

// ---- API Functions ----

export async function getAiProfile(): Promise<AiProfileResponse> {
  return api.get<AiProfileResponse>("/api/ai/profile");
}

export async function updateAiProfile(data: UpdateAiProfileRequest): Promise<AiProfileResponse> {
  return api.put<AiProfileResponse>("/api/ai/profile", data);
}

export async function getAiCostSummary(): Promise<AiCostSummaryResponse> {
  return api.get<AiCostSummaryResponse>("/api/ai/cost-summary");
}
