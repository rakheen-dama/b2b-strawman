"use server";

import { revalidatePath } from "next/cache";
import { api, API_BASE, getAuthFetchOptions } from "@/lib/api";
import type {
  BankStatementResponse,
  TrustReconciliationResponse,
  MatchResultResponse,
} from "@/lib/types";

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

export async function fetchReconciliations(
  accountId: string,
  params: {
    page?: number;
    size?: number;
  } = {},
): Promise<ReconciliationPage> {
  const queryParams = new URLSearchParams();
  queryParams.set("page", String(params.page ?? 0));
  queryParams.set("size", String(params.size ?? 20));
  queryParams.set("sort", "periodEnd,desc");

  const qs = queryParams.toString();
  const result = await api.get<PaginatedResponse<TrustReconciliationResponse>>(
    `/api/trust-accounts/${accountId}/reconciliations${qs ? `?${qs}` : ""}`,
  );

  return {
    content: result.content,
    totalElements: result.page.totalElements,
    totalPages: result.page.totalPages,
    pageSize: result.page.size,
    pageNumber: result.page.number,
  };
}

export async function fetchReconciliation(
  reconciliationId: string,
): Promise<TrustReconciliationResponse> {
  return api.get<TrustReconciliationResponse>(
    `/api/trust-reconciliations/${reconciliationId}`,
  );
}

// ── Bank statement actions ────────────────────────────────────────

const MAX_STATEMENT_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

export async function uploadBankStatement(
  accountId: string,
  file: File,
): Promise<{ success: boolean; data?: BankStatementResponse; error?: string }> {
  // Server-side validation — the UI `accept` attribute is not a security boundary
  if (
    file.type &&
    file.type !== "text/csv" &&
    file.type !== "application/vnd.ms-excel"
  ) {
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

    const response = await fetch(
      `${API_BASE}/api/trust-accounts/${accountId}/bank-statements`,
      {
        method: "POST",
        headers: {
          ...authOptions.headers,
          // NOTE: Do NOT set Content-Type -- browser sets multipart boundary automatically
        },
        body: formData,
        credentials: authOptions.credentials,
      },
    );

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
      error:
        error instanceof Error ? error.message : "Failed to upload statement",
    };
  }
}

export async function fetchBankStatements(
  accountId: string,
): Promise<BankStatementResponse[]> {
  const result = await api.get<PaginatedResponse<BankStatementResponse>>(
    `/api/trust-accounts/${accountId}/bank-statements?size=100&sort=createdAt,desc`,
  );
  return result.content;
}

export async function fetchBankStatement(
  statementId: string,
): Promise<BankStatementResponse> {
  return api.get<BankStatementResponse>(
    `/api/bank-statements/${statementId}`,
  );
}

// ── Matching actions ──────────────────────────────────────────────

export async function autoMatch(
  statementId: string,
): Promise<{ success: boolean; data?: MatchResultResponse; error?: string }> {
  try {
    const data = await api.post<MatchResultResponse>(
      `/api/bank-statements/${statementId}/auto-match`,
    );
    revalidatePath("/", "layout");
    return { success: true, data };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error ? error.message : "Failed to auto-match",
    };
  }
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
    await api.post(`/api/bank-statement-lines/${lineId}/exclude`, {
      reason,
    });
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

// ── Reconciliation lifecycle actions ──────────────────────────────

export async function createReconciliation(
  accountId: string,
  data: { periodEnd: string; bankStatementId: string },
): Promise<{
  success: boolean;
  data?: TrustReconciliationResponse;
  error?: string;
}> {
  try {
    const result = await api.post<TrustReconciliationResponse>(
      `/api/trust-accounts/${accountId}/reconciliations`,
      data,
    );
    revalidatePath("/", "layout");
    return { success: true, data: result };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to create reconciliation",
    };
  }
}

export async function calculateReconciliation(
  reconciliationId: string,
): Promise<{
  success: boolean;
  data?: TrustReconciliationResponse;
  error?: string;
}> {
  try {
    const result = await api.post<TrustReconciliationResponse>(
      `/api/trust-reconciliations/${reconciliationId}/calculate`,
    );
    return { success: true, data: result };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to calculate reconciliation",
    };
  }
}

export async function completeReconciliation(
  reconciliationId: string,
): Promise<{
  success: boolean;
  data?: TrustReconciliationResponse;
  error?: string;
}> {
  try {
    const result = await api.post<TrustReconciliationResponse>(
      `/api/trust-reconciliations/${reconciliationId}/complete`,
    );
    revalidatePath("/", "layout");
    return { success: true, data: result };
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
