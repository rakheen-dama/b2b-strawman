"use server";

import { api } from "@/lib/api";
import type { PersonalDashboardResponse } from "@/lib/dashboard-types";

export async function fetchPersonalDashboard(
  from: string,
  to: string
): Promise<PersonalDashboardResponse | null> {
  try {
    return await api.get<PersonalDashboardResponse>(
      `/api/dashboard/personal?from=${from}&to=${to}`
    );
  } catch {
    return null;
  }
}
