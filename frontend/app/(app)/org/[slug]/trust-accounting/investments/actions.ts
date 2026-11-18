"use server";

import { api } from "@/lib/api";
import type { PlaceInvestmentRequest, TrustInvestment } from "@/lib/types/trust";
import type { PlaceInvestmentFormData, WithdrawInvestmentFormData } from "@/lib/schemas/trust";

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

/**
 * Places an investment from the funding trust banking account `accountId` into the chosen
 * investment account (648A). A section 86(4) investment names the client and the matter whose
 * money it is; a section 86(3) investment names neither (the backend refuses either with 400).
 */
export async function placeInvestment(
  accountId: string,
  data: PlaceInvestmentFormData
): Promise<{ success: boolean; investment?: TrustInvestment; error?: string }> {
  try {
    const payload: PlaceInvestmentRequest = {
      investmentAccountId: data.investmentAccountId,
      institution: data.institution,
      accountNumber: data.accountNumber,
      principal: data.principal,
      // Backend stores interestRate as decimal (0.075 = 7.5%), form collects percentage
      interestRate: data.interestRate / 100,
      depositDate: data.depositDate,
      maturityDate: data.maturityDate || null,
      notes: data.notes || null,
      investmentBasis: data.investmentBasis,
    };
    if (data.investmentBasis === "CLIENT_INSTRUCTION") {
      payload.clientId = data.customerId;
      // G89-F28: the matter whose trust money is invested (required)
      payload.matterId = data.projectId;
    }
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

/** The whole investment returns on the date (648A); the amount is derived, never typed. */
export async function withdrawInvestment(
  investmentId: string,
  data: WithdrawInvestmentFormData
): Promise<{ success: boolean; investment?: TrustInvestment; error?: string }> {
  try {
    const investment = await api.post<TrustInvestment>(
      `/api/trust-investments/${investmentId}/withdraw`,
      { withdrawalDate: data.withdrawalDate }
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
