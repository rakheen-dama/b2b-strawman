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
