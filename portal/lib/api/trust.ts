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
  /** The client's portal matter name; null when the matter is not in the portal read model (633B). */
  matterName: string | null;
  /**
   * General (client ledger) balance — BigDecimal serialised as a number via Jackson. The client's
   * total held in trust is `currentBalance + investedBalance` (a placement does not move it).
   */
  currentBalance: number;
  /** Held for the client in section 86(4) investments on this matter (Phase 92 648D); 0 when none. */
  investedBalance: number;
  /** ISO 8601 instant. */
  lastTransactionAt: string;
  /** ISO 8601 instant. */
  lastSyncedAt: string;
}

/**
 * The client's total held in trust on a matter (Phase 92 648D, ADR-379): the general balance plus
 * what is invested for them under section 86(4) — invested money is still trust money, so a
 * placement does not move it. Rounded to the cent (the API sends two-decimal numbers).
 */
export function totalHeldInTrust(
  balances: Pick<
    PortalTrustMatterSummary,
    "currentBalance" | "investedBalance"
  >,
): number {
  return (
    Math.round((balances.currentBalance + balances.investedBalance) * 100) / 100
  );
}

export interface PortalTrustSummaryResponse {
  matters: PortalTrustMatterSummary[];
}

/** The ledger a portal trust row moved (Phase 92 648D) — for logic only, never rendered. */
export type PortalTrustLedger = "CLIENT" | "CLIENT_INVESTMENT";

export interface PortalTrustTransactionResponse {
  id: string;
  /** The type code (tenant chk_trust_txn_type) — for logic only, never rendered: render `typeLabel`. */
  transactionType: string;
  /** The client-facing label from the backend type registry, e.g. "Section 86(4) investment interest". */
  typeLabel: string;
  /** The ledger the row moved: `runningBalance` is that ledger's balance (648D). */
  ledger: PortalTrustLedger;
  amount: number;
  /** Running balance of the row's own ledger — the invested balance on a CLIENT_INVESTMENT row. */
  runningBalance: number;
  /** "YYYY-MM-DD" SA business date of the transaction — the date to show (G89-F39). */
  transactionDate: string;
  /** ISO 8601 instant the transaction was approved / recorded — not for display. */
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
