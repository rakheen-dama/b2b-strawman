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
  // F-667A-10: the filters run on the server across every ledger, so the page and its totals
  // describe the filtered set.
  const queryParams = new URLSearchParams();
  if (params.nonZeroOnly) queryParams.set("nonZeroOnly", "true");
  if (params.search) queryParams.set("search", params.search);
  queryParams.set("page", String(params.page ?? 0));
  queryParams.set("size", String(params.size ?? 20));
  queryParams.set("sort", "balance,desc");

  const qs = queryParams.toString();
  const result = await api.get<PaginatedResponse<ClientLedgerCard>>(
    `/api/trust-accounts/${accountId}/client-ledgers?${qs}`
  );

  return {
    content: result.content,
    totalElements: result.page.totalElements,
    totalPages: result.page.totalPages,
    pageSize: result.page.size,
    pageNumber: result.page.number,
  };
}

export async function fetchClientLedger(
  accountId: string,
  clientId: string
): Promise<ClientLedgerCard> {
  return api.get<ClientLedgerCard>(`/api/trust-accounts/${accountId}/client-ledgers/${clientId}`);
}

export async function fetchClientHistory(
  accountId: string,
  clientId: string,
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
    `/api/trust-accounts/${accountId}/client-ledgers/${clientId}/history?${qs}`
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
  clientId: string,
  startDate: string,
  endDate: string
): Promise<LedgerStatementResponse> {
  const queryParams = new URLSearchParams();
  queryParams.set("startDate", startDate);
  queryParams.set("endDate", endDate);

  const qs = queryParams.toString();
  return api.get<LedgerStatementResponse>(
    `/api/trust-accounts/${accountId}/client-ledgers/${clientId}/statement?${qs}`
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
