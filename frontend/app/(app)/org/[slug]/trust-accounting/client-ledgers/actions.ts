"use server";

import { api } from "@/lib/api";
import { exportReportPdf } from "@/lib/api/reports";
import type { ClientLedgerCard, TrustTransaction, LedgerStatementResponse } from "@/lib/types";

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

export interface ClientLedgerPage {
  content: ClientLedgerCard[];
  totalElements: number;
  totalPages: number;
  pageSize: number;
  pageNumber: number;
}

export interface ClientHistoryPage {
  content: TrustTransaction[];
  totalElements: number;
  totalPages: number;
  pageSize: number;
  pageNumber: number;
}

// ── Fetch actions ─────────────────────────────────────────────────

export async function fetchClientLedgers(
  accountId: string,
  params: {
    page?: number;
    size?: number;
    nonZeroOnly?: boolean;
    search?: string;
  } = {}
): Promise<ClientLedgerPage> {
  const queryParams = new URLSearchParams();
  queryParams.set("page", String(params.page ?? 0));
  queryParams.set("size", String(params.size ?? 20));
  queryParams.set("sort", "balance,desc");

  const qs = queryParams.toString();
  const result = await api.get<PaginatedResponse<ClientLedgerCard>>(
    `/api/trust-accounts/${accountId}/client-ledgers?${qs}`
  );

  let content = result.content;

  // Client-side filtering for non-zero balance and search
  if (params.nonZeroOnly) {
    content = content.filter((c) => c.balance !== 0);
  }
  if (params.search) {
    const term = params.search.toLowerCase();
    content = content.filter((c) => c.customerName.toLowerCase().includes(term));
  }

  // Recalculate pagination metadata after client-side filtering.
  // NOTE: This is only accurate when all matching data fits on one page.
  // For true multi-page filtered results, backend filtering would be needed.
  const filteredTotal = content.length;
  const pageSize = result.page.size;

  return {
    content,
    totalElements: filteredTotal,
    totalPages: filteredTotal > 0 ? Math.ceil(filteredTotal / pageSize) : 0,
    pageSize,
    pageNumber: result.page.number,
  };
}

export async function fetchClientLedger(
  accountId: string,
  customerId: string
): Promise<ClientLedgerCard> {
  return api.get<ClientLedgerCard>(`/api/trust-accounts/${accountId}/client-ledgers/${customerId}`);
}

export async function fetchClientHistory(
  accountId: string,
  customerId: string,
  params: {
    page?: number;
    size?: number;
    type?: string;
    status?: string;
    dateFrom?: string;
    dateTo?: string;
  } = {}
): Promise<ClientHistoryPage> {
  const queryParams = new URLSearchParams();
  if (params.type) queryParams.set("type", params.type);
  if (params.status) queryParams.set("status", params.status);
  if (params.dateFrom) queryParams.set("dateFrom", params.dateFrom);
  if (params.dateTo) queryParams.set("dateTo", params.dateTo);
  queryParams.set("page", String(params.page ?? 0));
  queryParams.set("size", String(params.size ?? 20));
  queryParams.set("sort", "transactionDate,desc");

  const qs = queryParams.toString();
  const result = await api.get<PaginatedResponse<TrustTransaction>>(
    `/api/trust-accounts/${accountId}/client-ledgers/${customerId}/history?${qs}`
  );

  return {
    content: result.content,
    totalElements: result.page.totalElements,
    totalPages: result.page.totalPages,
    pageSize: result.page.size,
    pageNumber: result.page.number,
  };
}

export async function fetchClientStatement(
  accountId: string,
  customerId: string,
  startDate: string,
  endDate: string
): Promise<LedgerStatementResponse> {
  const queryParams = new URLSearchParams();
  queryParams.set("startDate", startDate);
  queryParams.set("endDate", endDate);

  const qs = queryParams.toString();
  return api.get<LedgerStatementResponse>(
    `/api/trust-accounts/${accountId}/client-ledgers/${customerId}/statement?${qs}`
  );
}

// ── Statement PDF generation ──────────────────────────────────────

export async function generateStatementPdf(
  accountId: string,
  customerId: string,
  dateFrom: string,
  dateTo: string
): Promise<string> {
  return exportReportPdf("client-ledger-statement", {
    trust_account_id: accountId,
    customer_id: customerId,
    dateFrom,
    dateTo,
  });
}
