import { portalGet } from "@/lib/api-client";

/**
 * Portal trust ledger API client.
 *
 * Backend (495A): routes live under `/portal/trust/*`. Every endpoint is
 * module-gated server-side — if `trust_accounting` is disabled for the
 * authenticated customer's tenant, the backend returns **404** (not 403).
 */

export interface PortalTrustMatterSummary {
  matterId: string;
  /** BigDecimal serialised as a number via Jackson. */
  currentBalance: number;
  /** ISO 8601 instant. */
  lastTransactionAt: string;
  /** ISO 8601 instant. */
  lastSyncedAt: string;
}

export interface PortalTrustSummaryResponse {
  matters: PortalTrustMatterSummary[];
}

export interface PortalTrustTransactionResponse {
  id: string;
  /** e.g. "DEPOSIT" | "WITHDRAWAL" | "INTEREST_POSTED" | "RECONCILIATION" */
  transactionType: string;
  amount: number;
  runningBalance: number;
  occurredAt: string;
  description: string;
  reference: string;
}

export interface PortalTrustStatementDocumentResponse {
  id: string;
  fileName: string;
  generatedAt: string;
  /** Direct signed URL — open with `window.open(url, "_blank")`. */
  downloadUrl: string;
}

/** Spring `Page<T>` response shape. */
export interface PageResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

export interface MatterTransactionsParams {
  page?: number;
  size?: number;
  /** ISO 8601 instant, optional. */
  from?: string;
  /** ISO 8601 instant, optional. */
  to?: string;
}

/**
 * Fetches per-matter trust balance snapshots for the authenticated customer.
 */
export async function getTrustSummary(): Promise<PortalTrustSummaryResponse> {
  return portalGet<PortalTrustSummaryResponse>("/portal/trust/summary");
}

/**
 * Fetches a page of trust transactions for the given matter, newest first.
 */
export async function getMatterTransactions(
  matterId: string,
  params: MatterTransactionsParams = {},
): Promise<PageResponse<PortalTrustTransactionResponse>> {
  const qs = new URLSearchParams();
  if (params.page !== undefined) qs.set("page", String(params.page));
  if (params.size !== undefined) qs.set("size", String(params.size));
  if (params.from) qs.set("from", params.from);
  if (params.to) qs.set("to", params.to);
  const query = qs.toString();
  const path = `/portal/trust/matters/${encodeURIComponent(matterId)}/transactions${
    query ? `?${query}` : ""
  }`;
  return portalGet<PageResponse<PortalTrustTransactionResponse>>(path);
}

/**
 * Fetches the list of published trust statement documents for the matter.
 * Each entry carries a signed `downloadUrl` — no second round-trip needed.
 */
export async function getMatterStatementDocuments(
  matterId: string,
): Promise<PortalTrustStatementDocumentResponse[]> {
  return portalGet<PortalTrustStatementDocumentResponse[]>(
    `/portal/trust/matters/${encodeURIComponent(matterId)}/statement-documents`,
  );
}

/**
 * Builds a human-readable matter label. The 495A DTO does NOT expose a matter
 * display name, so we fall back to a short-id ("Matter ab12cd34"). Replace
 * once a richer endpoint lands.
 */
export function formatMatterLabel(matterId: string): string {
  const short = matterId.replace(/-/g, "").slice(0, 8);
  return `Matter ${short}`;
}
