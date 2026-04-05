"use server";

import { api } from "@/lib/api";
import type {
  ClientLedgerCard,
  ClientLedgerResponse,
  TrustTransaction,
  LedgerStatementResponse,
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
    nonZeroOnly?: boolean;
    search?: string;
    page?: number;
    size?: number;
    sort?: string;
  } = {},
): Promise<ClientLedgerPage> {
  const queryParams = new URLSearchParams();
  if (params.nonZeroOnly) queryParams.set("nonZeroOnly", "true");
  if (params.search) queryParams.set("search", params.search);
  queryParams.set("page", String(params.page ?? 0));
  queryParams.set("size", String(params.size ?? 20));
  queryParams.set("sort", params.sort ?? "balance,desc");

  const qs = queryParams.toString();
  const result = await api.get<PaginatedResponse<ClientLedgerCard>>(
    `/api/trust-accounts/${accountId}/client-ledgers${qs ? `?${qs}` : ""}`,
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
  customerId: string,
): Promise<ClientLedgerResponse> {
  return api.get<ClientLedgerResponse>(
    `/api/trust-accounts/${accountId}/client-ledgers/${customerId}`,
  );
}

export async function fetchClientHistory(
  accountId: string,
  customerId: string,
  params: {
    dateFrom?: string;
    dateTo?: string;
    type?: string;
    status?: string;
    page?: number;
    size?: number;
  } = {},
): Promise<ClientHistoryPage> {
  const queryParams = new URLSearchParams();
  if (params.dateFrom) queryParams.set("dateFrom", params.dateFrom);
  if (params.dateTo) queryParams.set("dateTo", params.dateTo);
  if (params.type) queryParams.set("type", params.type);
  if (params.status) queryParams.set("status", params.status);
  queryParams.set("page", String(params.page ?? 0));
  queryParams.set("size", String(params.size ?? 20));
  queryParams.set("sort", "transactionDate,desc");

  const qs = queryParams.toString();
  const result = await api.get<PaginatedResponse<TrustTransaction>>(
    `/api/trust-accounts/${accountId}/client-ledgers/${customerId}/history${qs ? `?${qs}` : ""}`,
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
  params: {
    dateFrom?: string;
    dateTo?: string;
  } = {},
): Promise<LedgerStatementResponse> {
  const queryParams = new URLSearchParams();
  if (params.dateFrom) queryParams.set("dateFrom", params.dateFrom);
  if (params.dateTo) queryParams.set("dateTo", params.dateTo);

  const qs = queryParams.toString();
  return api.get<LedgerStatementResponse>(
    `/api/trust-accounts/${accountId}/client-ledgers/${customerId}/statement${qs ? `?${qs}` : ""}`,
  );
}
