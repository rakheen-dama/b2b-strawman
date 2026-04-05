"use server";

import { revalidatePath } from "next/cache";
import { unstable_rethrow } from "next/navigation";
import { api } from "@/lib/api";
import type { TrustInvestment } from "@/lib/types";
import {
  placeInvestmentSchema,
  recordInvestmentInterestSchema,
  type PlaceInvestmentFormData,
  type RecordInvestmentInterestFormData,
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
  const parsed = placeInvestmentSchema.safeParse(data);
  if (!parsed.success)
    return { success: false, error: "Invalid investment payload" };

  try {
    await api.post(`/api/trust-accounts/${accountId}/investments`, {
      customerId: parsed.data.customerId,
      institution: parsed.data.institution,
      accountNumber: parsed.data.accountNumber,
      principal: parsed.data.principal,
      interestRate: parsed.data.interestRate,
      depositDate: parsed.data.depositDate,
      maturityDate: parsed.data.maturityDate || null,
      notes: parsed.data.notes || null,
    });
    revalidatePath("/org/[slug]/trust-accounting/investments", "page");
    return { success: true };
  } catch (error) {
    unstable_rethrow(error);
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
  const parsed = recordInvestmentInterestSchema.safeParse(data);
  if (!parsed.success)
    return { success: false, error: "Invalid interest payload" };

  try {
    await api.put(`/api/trust-investments/${investmentId}/interest`, {
      amount: parsed.data.amount,
    });
    revalidatePath("/org/[slug]/trust-accounting/investments", "page");
    return { success: true };
  } catch (error) {
    unstable_rethrow(error);
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
    revalidatePath("/org/[slug]/trust-accounting/investments", "page");
    return { success: true };
  } catch (error) {
    unstable_rethrow(error);
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to withdraw investment",
    };
  }
}
