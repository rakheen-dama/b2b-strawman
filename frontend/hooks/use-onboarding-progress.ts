"use client";

import { useCallback } from "react";
import useSWR from "swr";
import {
  fetchOnboardingProgress,
  dismissOnboarding,
  type OnboardingProgressResponse,
} from "@/lib/actions/onboarding";

export interface OnboardingProgress {
  steps: { code: string; completed: boolean }[];
  completedCount: number;
  totalCount: number;
  percentComplete: number;
  allComplete: boolean;
  dismissed: boolean;
  loading: boolean;
}

export function useOnboardingProgress() {
  const { data, isLoading, mutate } = useSWR<OnboardingProgressResponse>(
    "onboarding-progress",
    () => fetchOnboardingProgress(),
    {
      dedupingInterval: 5000,
      errorRetryCount: 1,
    }
  );

  const dismiss = useCallback(async () => {
    const result = await dismissOnboarding();
    if (result.success) {
      await mutate();
    }
    return result;
  }, [mutate]);

  const percentComplete =
    data && data.totalCount > 0
      ? Math.round((data.completedCount / data.totalCount) * 100)
      : 0;
  const allComplete = data ? data.completedCount === data.totalCount : false;

  return {
    steps: data?.steps ?? [],
    completedCount: data?.completedCount ?? 0,
    totalCount: data?.totalCount ?? 0,
    percentComplete,
    allComplete,
    dismissed: data?.dismissed ?? false,
    loading: isLoading,
    dismiss,
  };
}
