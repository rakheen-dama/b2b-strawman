"use client";

import { useCallback, useEffect, useRef, useState } from "react";
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
  const [data, setData] = useState<OnboardingProgressResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const mountedRef = useRef(true);

  const refetch = useCallback(async () => {
    try {
      const result = await fetchOnboardingProgress();
      if (mountedRef.current) {
        setData(result);
      }
    } catch {
      // Silently ignore — card stays hidden if fetch fails
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    refetch();
    return () => {
      mountedRef.current = false;
    };
  }, [refetch]);

  const dismiss = useCallback(async () => {
    const result = await dismissOnboarding();
    if (result.success) {
      await refetch();
    }
    return result;
  }, [refetch]);

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
    loading,
    dismiss,
  };
}
