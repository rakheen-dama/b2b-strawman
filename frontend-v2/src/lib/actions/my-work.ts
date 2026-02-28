"use server";

import { api } from "@/lib/api";
import type { MyWorkTimeSummary } from "@/lib/types";

export async function fetchMyTimeSummaryAction(
  from: string,
  to: string,
): Promise<MyWorkTimeSummary | null> {
  try {
    return await api.get<MyWorkTimeSummary>(
      `/api/my-work/time-summary?from=${from}&to=${to}`,
    );
  } catch {
    return null;
  }
}
