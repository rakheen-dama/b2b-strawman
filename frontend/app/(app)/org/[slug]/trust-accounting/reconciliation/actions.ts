"use server";

import { revalidatePath } from "next/cache";
import { api } from "@/lib/api";
import type {
  BankStatement,
  TrustReconciliation,
  MatchResult,
  TrustTransaction,
} from "@/lib/types";

// ── Response types ─────────────────────────────────────────────────

interface ActionResult {
  success: boolean;
  error?: string;
}

// ── Bank Statement actions ─────────────────────────────────────────

export async function fetchBankStatements(
  accountId: string,
): Promise<BankStatement[]> {
  return api.get<BankStatement[]>(
    `/api/trust-accounts/${accountId}/bank-statements`,
  );
}

export async function fetchBankStatement(
  statementId: string,
): Promise<BankStatement> {
  return api.get<BankStatement>(`/api/bank-statements/${statementId}`);
}

export async function uploadBankStatement(
  accountId: string,
  formData: FormData,
): Promise<BankStatement> {
  return api.post<BankStatement>(
    `/api/trust-accounts/${accountId}/bank-statements`,
    formData,
  );
}

// ── Matching actions ───────────────────────────────────────────────

export async function autoMatch(statementId: string): Promise<MatchResult> {
  return api.post<MatchResult>(
    `/api/bank-statements/${statementId}/auto-match`,
  );
}

export async function manualMatch(
  lineId: string,
  transactionId: string,
): Promise<ActionResult> {
  try {
    await api.post(`/api/bank-statement-lines/${lineId}/match`, {
      transactionId,
    });
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error ? error.message : "Failed to match line",
    };
  }
}

export async function unmatch(lineId: string): Promise<ActionResult> {
  try {
    await api.post(`/api/bank-statement-lines/${lineId}/unmatch`);
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error ? error.message : "Failed to unmatch line",
    };
  }
}

export async function excludeLine(
  lineId: string,
  reason: string,
): Promise<ActionResult> {
  try {
    await api.post(`/api/bank-statement-lines/${lineId}/exclude`, { reason });
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error ? error.message : "Failed to exclude line",
    };
  }
}

// ── Reconciliation actions ─────────────────────────────────────────

export async function fetchReconciliations(
  accountId: string,
): Promise<TrustReconciliation[]> {
  return api.get<TrustReconciliation[]>(
    `/api/trust-accounts/${accountId}/reconciliations`,
  );
}

export async function fetchReconciliation(
  reconciliationId: string,
): Promise<TrustReconciliation> {
  return api.get<TrustReconciliation>(
    `/api/trust-reconciliations/${reconciliationId}`,
  );
}

export async function createReconciliation(
  accountId: string,
  periodEnd: string,
  bankStatementId: string,
): Promise<TrustReconciliation> {
  return api.post<TrustReconciliation>(
    `/api/trust-accounts/${accountId}/reconciliations`,
    { periodEnd, bankStatementId },
  );
}

export async function calculateReconciliation(
  reconciliationId: string,
): Promise<TrustReconciliation> {
  return api.post<TrustReconciliation>(
    `/api/trust-reconciliations/${reconciliationId}/calculate`,
  );
}

export async function completeReconciliation(
  reconciliationId: string,
): Promise<ActionResult> {
  try {
    await api.post(
      `/api/trust-reconciliations/${reconciliationId}/complete`,
    );
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to complete reconciliation",
    };
  }
}

// ── Unmatched transactions (for split-pane) ────────────────────────

export async function fetchUnmatchedTransactions(
  accountId: string,
): Promise<TrustTransaction[]> {
  return api.get<TrustTransaction[]>(
    `/api/trust-accounts/${accountId}/transactions?status=APPROVED&unmatched=true&size=500`,
  );
}
