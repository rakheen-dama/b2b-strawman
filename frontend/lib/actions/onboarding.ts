"use server";

import { api, ApiError } from "@/lib/api";

export interface OnboardingStep {
  code: string;
  completed: boolean;
}

export interface OnboardingProgressResponse {
  steps: OnboardingStep[];
  dismissed: boolean;
  completedCount: number;
  totalCount: number;
}

export interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchOnboardingProgress(): Promise<OnboardingProgressResponse> {
  return api.get<OnboardingProgressResponse>("/api/onboarding/progress");
}

export async function dismissOnboarding(): Promise<ActionResult> {
  try {
    await api.post("/api/onboarding/dismiss");
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  return { success: true };
}
