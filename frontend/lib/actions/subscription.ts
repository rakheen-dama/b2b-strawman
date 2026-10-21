"use server";

import { api } from "@/lib/api";
import type { BillingResponse } from "@/lib/internal-api";

export async function getSubscription(): Promise<BillingResponse> {
  return api.get<BillingResponse>("/api/billing/subscription");
}
