"use server";

import { api } from "@/lib/api";
import type {
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

export interface CreateTrustAccountInput {
  accountName: string;
  bankName: string;
  branchCode: string;
  accountNumber: string;
  accountType?: "GENERAL" | "INVESTMENT";
  isPrimary?: boolean;
  requireDualApproval?: boolean;
  paymentApprovalThreshold?: number | null;
  openedDate?: string;
  notes?: string;
}

export async function createTrustAccount(
  input: CreateTrustAccountInput,
): Promise<
  | { success: true; account: TrustAccount }
  | { success: false; error: string }
> {
  try {
    const account = await api.post<TrustAccount>(
      "/api/trust-accounts",
      input,
    );
    return { success: true, account };
  } catch (err) {
    const message =
      err instanceof Error ? err.message : "Failed to create trust account";
    return { success: false, error: message };
  }
}

// ── Transaction actions ────────────────────────────────────────────

export async function fetchRecentTransactions(
  accountId: string,
): Promise<TrustTransaction[]> {
  const result = await api.get<PaginatedResponse<TrustTransaction>>(
    `/api/trust-accounts/${accountId}/transactions?size=10&sort=transactionDate,desc`,
  );
  return result.content;
}

export async function fetchPendingApprovals(
  accountId: string,
): Promise<TrustTransaction[]> {
  return api.get<TrustTransaction[]>(
    `/api/trust-accounts/${accountId}/pending-approvals`,
  );
}

// ── Balance actions ────────────────────────────────────────────────

export async function fetchCashbookBalance(
  accountId: string,
): Promise<CashbookBalance> {
  return api.get<CashbookBalance>(
    `/api/trust-accounts/${accountId}/cashbook-balance`,
  );
}

// ── Dashboard aggregation ──────────────────────────────────────────

export async function fetchDashboardData(
  accountId: string,
): Promise<TrustDashboardData> {
  const [cashbook, ledgers, pendingApprovals, recentTransactions] =
    await Promise.all([
      fetchCashbookBalance(accountId),
      // TODO: Replace with dedicated count endpoint when backend supports it
      api.get<PaginatedResponse<ClientLedgerCard>>(
        `/api/trust-accounts/${accountId}/client-ledgers?size=200`,
      ),
      fetchPendingApprovals(accountId),
      fetchRecentTransactions(accountId),
    ]);

  // Count clients with non-zero balance as "active"
  const activeClients = ledgers.content.filter(
    (l) => l.balance !== 0,
  ).length;

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
    lastReconciliationDate: null, // Reconciliation module not yet implemented
    lastReconciliationBalanced: null,
    recentTransactions,
    alerts,
  };
}
