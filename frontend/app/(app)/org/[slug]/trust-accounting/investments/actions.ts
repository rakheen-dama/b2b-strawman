"use server";

import { api } from "@/lib/api";
import type { TrustInvestment } from "@/lib/types/trust";
import type { PlaceInvestmentFormData } from "@/lib/schemas/trust";

// -- Investments ---------------------------------------------------------

export async function fetchInvestments(
  accountId: string,
  investmentBasis?: string
): Promise<TrustInvestment[]> {
  let url = `/api/trust-accounts/${accountId}/investments?size=100&sort=depositDate,desc`;
  if (investmentBasis) {
    url += `&investmentBasis=${encodeURIComponent(investmentBasis)}`;
  }
  const response = await api.get<{
    content: TrustInvestment[];
    page: { totalElements: number; totalPages: number; size: number; number: number };
  }>(url);
  return response.content;
}

export async function placeInvestment(
  accountId: string,
  data: PlaceInvestmentFormData
): Promise<{ success: boolean; investment?: TrustInvestment; error?: string }> {
  try {
    // Backend stores interestRate as decimal (0.075 = 7.5%), form collects percentage
    const payload = {
      ...data,
      interestRate: data.interestRate / 100,
    };
    const investment = await api.post<TrustInvestment>(
      `/api/trust-accounts/${accountId}/investments`,
      payload
    );
    return { success: true, investment };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to place investment";
    return { success: false, error: message };
  }
}

export async function recordInterest(
  investmentId: string,
  amount: number
): Promise<{ success: boolean; investment?: TrustInvestment; error?: string }> {
  try {
    const investment = await api.put<TrustInvestment>(
      `/api/trust-investments/${investmentId}/interest`,
      { amount }
    );
    return { success: true, investment };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to record interest";
    return { success: false, error: message };
  }
}

export async function withdrawInvestment(
  investmentId: string
): Promise<{ success: boolean; investment?: TrustInvestment; error?: string }> {
  try {
    const investment = await api.post<TrustInvestment>(
      `/api/trust-investments/${investmentId}/withdraw`,
      {}
    );
    return { success: true, investment };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to withdraw investment";
    return { success: false, error: message };
  }
}

export async function fetchMaturing(accountId: string): Promise<TrustInvestment[]> {
  return api.get<TrustInvestment[]>(`/api/trust-accounts/${accountId}/investments/maturing`);
}
