"use server";

import { revalidatePath } from "next/cache";
import { api, API_BASE, ApiError, getAuthFetchOptions } from "@/lib/api";
import type {
  BankStatementResponse,
  TrustReconciliationResponse,
  MatchResultResponse,
  RecordFromLineRequest,
  RecordFromLineResponse,
  MonthlyList,
} from "@/lib/types";
import type { RecordFromStatementFormData } from "@/lib/schemas/trust";

// ── Response types ─────────────────────────────────────────────────

export interface ReconciliationPage {
  content: TrustReconciliationResponse[];
  totalElements: number;
  totalPages: number;
  pageSize: number;
  pageNumber: number;
}

interface ActionResult {
  success: boolean;
  error?: string;
}

// ── Reconciliation fetch actions ──────────────────────────────────

// The backend list endpoints return a plain JSON array (`ResponseEntity<List<…>>`, ordered by
// period end descending), not a Spring `Page` envelope (G89-F17), so paging is applied here to keep
// the page component's shape.
export async function fetchReconciliations(
  accountId: string,
  params: {
    page?: number;
    size?: number;
  } = {}
): Promise<ReconciliationPage> {
  const pageNumber = params.page ?? 0;
  const pageSize = params.size ?? 20;
  const all = await api.get<TrustReconciliationResponse[]>(
    `/api/trust-accounts/${accountId}/reconciliations`
  );

  return {
    content: all.slice(pageNumber * pageSize, (pageNumber + 1) * pageSize),
    totalElements: all.length,
    totalPages: Math.ceil(all.length / pageSize),
    pageSize,
    pageNumber,
  };
}

export async function fetchReconciliation(
  reconciliationId: string
): Promise<TrustReconciliationResponse> {
  return api.get<TrustReconciliationResponse>(`/api/trust-reconciliations/${reconciliationId}`);
}

// 650A (ADR-382): one locked Rule 54.15 monthly trust creditor list, with the entries made after
// the lock. Read-only; a list is locked only by completing a month-end reconciliation.
export async function fetchMonthlyList(listId: string): Promise<MonthlyList> {
  return api.get<MonthlyList>(`/api/trust-monthly-lists/${listId}`);
}

// ── Bank statement actions ────────────────────────────────────────

const MAX_STATEMENT_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

export async function uploadBankStatement(
  accountId: string,
  file: File
): Promise<{ success: boolean; data?: BankStatementResponse; error?: string }> {
  // Server-side validation — the UI `accept` attribute is not a security boundary
  if (file.type && file.type !== "text/csv" && file.type !== "application/vnd.ms-excel") {
    return { success: false, error: "Only CSV files are accepted" };
  }
  if (!file.name.toLowerCase().endsWith(".csv")) {
    return { success: false, error: "File must have a .csv extension" };
  }
  if (file.size > MAX_STATEMENT_SIZE_BYTES) {
    return {
      success: false,
      error: `File exceeds the 10 MB limit (${(file.size / 1024 / 1024).toFixed(1)} MB)`,
    };
  }

  try {
    const authOptions = await getAuthFetchOptions("POST");

    const formData = new FormData();
    formData.append("file", file);

    const response = await fetch(`${API_BASE}/api/trust-accounts/${accountId}/bank-statements`, {
      method: "POST",
      headers: {
        ...authOptions.headers,
        // NOTE: Do NOT set Content-Type -- browser sets multipart boundary automatically
      },
      body: formData,
      credentials: authOptions.credentials,
    });

    if (!response.ok) {
      let message = response.statusText;
      try {
        const detail = await response.json();
        message = detail?.detail || detail?.title || message;
      } catch {
        // ignore
      }
      return { success: false, error: message };
    }

    const data = (await response.json()) as BankStatementResponse;
    revalidatePath("/", "layout");
    return { success: true, data };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Failed to upload statement",
    };
  }
}

export async function fetchBankStatements(accountId: string): Promise<BankStatementResponse[]> {
  return api.get<BankStatementResponse[]>(`/api/trust-accounts/${accountId}/bank-statements`);
}

export async function fetchBankStatement(statementId: string): Promise<BankStatementResponse> {
  return api.get<BankStatementResponse>(`/api/bank-statements/${statementId}`);
}

// ── Matching actions ──────────────────────────────────────────────

export async function autoMatch(
  statementId: string
): Promise<{ success: boolean; data?: MatchResultResponse; error?: string }> {
  try {
    const data = await api.post<MatchResultResponse>(
      `/api/bank-statements/${statementId}/auto-match`
    );
    revalidatePath("/", "layout");
    return { success: true, data };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Failed to auto-match",
    };
  }
}

export async function manualMatch(lineId: string, transactionId: string): Promise<ActionResult> {
  try {
    await api.post(`/api/bank-statement-lines/${lineId}/match`, {
      transactionId,
    });
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Failed to match line",
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
      error: error instanceof Error ? error.message : "Failed to unmatch line",
    };
  }
}

export async function excludeLine(lineId: string, reason: string): Promise<ActionResult> {
  try {
    await api.post(`/api/bank-statement-lines/${lineId}/exclude`, {
      reason,
    });
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Failed to exclude line",
    };
  }
}

/**
 * Record from statement (647A, ADR-380): posts the row the firm confirmed for an unmatched line.
 * Charge detail is sent only for a bank charge (the server refuses it on any other
 * classification). The error is the server's ProblemDetail detail, shown in the dialog.
 */
export async function recordFromStatement(
  lineId: string,
  input: RecordFromStatementFormData
): Promise<{ success: boolean; data?: RecordFromLineResponse; error?: string }> {
  const notes = input.notes ? input.notes : null;
  // Each classification sends only its own fields: the server refuses the others (648B)
  let body: RecordFromLineRequest = { classification: input.classification, notes };
  if (input.classification === "BANK_CHARGE") {
    body = {
      classification: input.classification,
      chargeRecoverable: input.chargeRecoverable,
      chargeVatAmount: input.chargeVatAmount,
      notes,
    };
  } else if (input.classification === "INVESTMENT_INTEREST") {
    body = {
      classification: input.classification,
      investmentId: input.investmentId,
      presentation: input.presentation,
      // GROSS: the line is the gross (the server takes it); NET: the gross the bank advised
      ...(input.presentation === "NET" ? { grossAmount: input.grossAmount } : {}),
      notes,
    };
  }
  try {
    const data = await api.post<RecordFromLineResponse>(
      `/api/bank-statement-lines/${lineId}/record`,
      body
    );
    revalidatePath("/", "layout");
    return { success: true, data };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Failed to record the line",
    };
  }
}

// ── Reconciliation lifecycle actions ──────────────────────────────

// 652C: create is get-or-create (an existing draft for the account and period end is returned,
// re-pointed to the statement). A refused create (409 "This period is already reconciled", or the
// period rules' 400s) carries its ProblemDetail title as well as its detail, so the wizard banner
// can head it. `errorTitle` is set only when it adds to `error`.
export async function createReconciliation(
  accountId: string,
  data: { periodEnd: string; bankStatementId: string }
): Promise<{
  success: boolean;
  data?: TrustReconciliationResponse;
  error?: string;
  errorTitle?: string;
}> {
  try {
    const result = await api.post<TrustReconciliationResponse>(
      `/api/trust-accounts/${accountId}/reconciliations`,
      data
    );
    revalidatePath("/", "layout");
    return { success: true, data: result };
  } catch (error) {
    const title = error instanceof ApiError ? error.detail?.title : undefined;
    const message = error instanceof Error ? error.message : "Failed to create reconciliation";
    return {
      success: false,
      error: message,
      ...(title && title !== message ? { errorTitle: title } : {}),
    };
  }
}

export async function calculateReconciliation(reconciliationId: string): Promise<{
  success: boolean;
  data?: TrustReconciliationResponse;
  error?: string;
}> {
  try {
    const result = await api.post<TrustReconciliationResponse>(
      `/api/trust-reconciliations/${reconciliationId}/calculate`
    );
    return { success: true, data: result };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Failed to calculate reconciliation",
    };
  }
}

// A refusal carries its ProblemDetail title as well as its detail (O-OBS-2): the gate (649C) puts
// the refused condition in the title ("Reconciliation does not balance") and its sentences in the
// detail. `errorTitle` is set only when it adds to `error` (the client falls back to the title).
export async function completeReconciliation(reconciliationId: string): Promise<{
  success: boolean;
  data?: TrustReconciliationResponse;
  error?: string;
  errorTitle?: string;
}> {
  try {
    const result = await api.post<TrustReconciliationResponse>(
      `/api/trust-reconciliations/${reconciliationId}/complete`
    );
    revalidatePath("/", "layout");
    return { success: true, data: result };
  } catch (error) {
    const title = error instanceof ApiError ? error.detail?.title : undefined;
    const message = error instanceof Error ? error.message : "Failed to complete reconciliation";
    return {
      success: false,
      error: message,
      ...(title && title !== message ? { errorTitle: title } : {}),
    };
  }
}
