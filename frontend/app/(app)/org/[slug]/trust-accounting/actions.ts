"use server";

import { api } from "@/lib/api";
import { matterPath } from "@/lib/api/matters";
import { settle } from "@/lib/settle";
import type { WidgetResult } from "@/lib/dashboard-home";
import type { TrustBalanceTrendResponse, TrustPositionResponse } from "@/lib/dashboard-types";
import type {
  MatterTrustBalance,
  TrustAccount,
  TrustTransaction,
  CashbookBalance,
  ClientLedgerCard,
  TrustDashboardData,
  TrustAlert,
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

// ── Trust Account actions ──────────────────────────────────────────

export async function fetchTrustAccounts(): Promise<TrustAccount[]> {
  return api.get<TrustAccount[]>("/api/trust-accounts");
}

/**
 * Matter Overview "Trust balance" tile (Phase 89 §14.3.18, G88-M13): the sum of the matter's
 * RECORDED/APPROVED trust transactions. 403 (no VIEW_TRUST) and 404 (matter not visible) settle to
 * "absent" — the tile drops out; 401 → branded re-login; anything else → "error".
 */
export async function fetchMatterTrustBalance(
  matterId: string
): Promise<WidgetResult<MatterTrustBalance>> {
  return settle(true, "matter trust balance", () =>
    api.get<MatterTrustBalance>(matterPath(matterId, "trust-balance"))
  );
}

export interface CreateTrustAccountInput {
  accountName: string;
  bankName: string;
  branchCode: string;
  accountNumber: string;
  accountType?: "GENERAL" | "INVESTMENT" | "SECTION_86";
  isPrimary?: boolean;
  requireDualApproval?: boolean;
  paymentApprovalThreshold?: number | null;
  openedDate?: string;
  notes?: string;
}

export type CreateTrustAccountResult =
  | { success: true; account: TrustAccount }
  | { success: false; error: string };

export async function createTrustAccount(
  input: CreateTrustAccountInput
): Promise<CreateTrustAccountResult> {
  try {
    const account = await api.post<TrustAccount>("/api/trust-accounts", input);
    return { success: true, account };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to create trust account";
    return { success: false, error: message };
  }
}

// ── Transaction actions ────────────────────────────────────────────

export async function fetchRecentTransactions(accountId: string): Promise<TrustTransaction[]> {
  const result = await api.get<PaginatedResponse<TrustTransaction>>(
    `/api/trust-accounts/${accountId}/transactions?size=10&sort=transactionDate,desc`
  );
  return result.content;
}

export async function fetchPendingApprovals(accountId: string): Promise<TrustTransaction[]> {
  return api.get<TrustTransaction[]>(`/api/trust-accounts/${accountId}/pending-approvals`);
}

// ── Balance actions ────────────────────────────────────────────────

export async function fetchCashbookBalance(accountId: string): Promise<CashbookBalance> {
  return api.get<CashbookBalance>(`/api/trust-accounts/${accountId}/cashbook-balance`);
}

// ── Dashboard aggregation ──────────────────────────────────────────

export async function fetchDashboardData(accountId: string): Promise<TrustDashboardData> {
  const [cashbook, ledgers, pendingApprovals, recentTransactions] = await Promise.all([
    fetchCashbookBalance(accountId),
    // TODO: Replace with dedicated count endpoint when backend supports it
    api.get<PaginatedResponse<ClientLedgerCard>>(
      `/api/trust-accounts/${accountId}/client-ledgers?size=200`
    ),
    fetchPendingApprovals(accountId),
    fetchRecentTransactions(accountId),
  ]);

  // Count clients with non-zero balance as "active"
  const activeClients = ledgers.content.filter((l) => l.balance !== 0).length;

  // Build alerts from pending approvals aging
  const alerts: TrustAlert[] = [];
  if (pendingApprovals.length > 0) {
    alerts.push({
      type: "AGING_APPROVAL",
      message: `${pendingApprovals.length} transaction${pendingApprovals.length === 1 ? "" : "s"} awaiting approval`,
      severity: "warning",
    });
  }

  return {
    totalBalance: cashbook.balance,
    activeClients,
    pendingApprovals: pendingApprovals.length,
    recentTransactions,
    alerts,
  };
}

/**
 * Trust position for the dashboard's StatCards (Phase 90 643A, §15.3.6) — the same read as Home's
 * trust widget, so the Reconciliation tile shows the latest reconciliation. 403/404 → absent;
 * 401 → re-login; anything else → error. A read, not a mutation.
 */
export async function fetchTrustPosition(
  accountId: string
): Promise<WidgetResult<TrustPositionResponse>> {
  return settle(true, "trust position", () =>
    api.get<TrustPositionResponse>(
      `/api/dashboard/trust-position?accountId=${encodeURIComponent(accountId)}`
    )
  );
}

/** Month-end balances for the trust balance trend chart (ADR-372). A read, not a mutation. */
export async function fetchTrustBalanceTrend(
  accountId: string,
  months = 12
): Promise<WidgetResult<TrustBalanceTrendResponse>> {
  return settle(true, "trust balance trend", () =>
    api.get<TrustBalanceTrendResponse>(
      `/api/dashboard/trust-balance-trend?accountId=${encodeURIComponent(accountId)}&months=${months}`
    )
  );
}
