"use server";

import { revalidatePath } from "next/cache";
import { api } from "@/lib/api";
import type { TrustInvestment } from "@/lib/types";
import type {
  PlaceInvestmentFormData,
  RecordInvestmentInterestFormData,
} from "@/lib/schemas/trust";

// ── Response types ─────────────────────────────────────────────────

interface PaginatedResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

interface ActionResult {
  success: boolean;
  error?: string;
}

// ── Fetch actions ─────────────────────────────────────────────────

export async function fetchInvestments(
  accountId: string,
): Promise<TrustInvestment[]> {
  const result = await api.get<PaginatedResponse<TrustInvestment>>(
    `/api/trust-accounts/${accountId}/investments?size=200&sort=depositDate,desc`,
  );
  return result.content;
}

export async function fetchMaturing(
  accountId: string,
  daysAhead = 30,
): Promise<TrustInvestment[]> {
  return api.get<TrustInvestment[]>(
    `/api/trust-accounts/${accountId}/investments/maturing?daysAhead=${daysAhead}`,
  );
}

// ── Mutation actions ──────────────────────────────────────────────

export async function placeInvestment(
  accountId: string,
  data: PlaceInvestmentFormData,
): Promise<ActionResult> {
  try {
    await api.post(`/api/trust-accounts/${accountId}/investments`, {
      customerId: data.customerId,
      institution: data.institution,
      accountNumber: data.accountNumber,
      principal: data.principal,
      interestRate: data.interestRate,
      depositDate: data.depositDate,
      maturityDate: data.maturityDate || null,
      notes: data.notes || null,
    });
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to place investment",
    };
  }
}

export async function recordInterest(
  investmentId: string,
  data: RecordInvestmentInterestFormData,
): Promise<ActionResult> {
  try {
    await api.put(`/api/trust-investments/${investmentId}/interest`, {
      amount: data.amount,
    });
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to record interest",
    };
  }
}

export async function withdrawInvestment(
  investmentId: string,
): Promise<ActionResult> {
  try {
    await api.post(`/api/trust-investments/${investmentId}/withdraw`);
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to withdraw investment",
    };
  }
}
