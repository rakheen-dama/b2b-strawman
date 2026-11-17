"use server";

import { api } from "@/lib/api";
import type { CreditorLedgerPage, TrustCreditorAccount } from "@/lib/types";

/** The trust account's non-client creditor ledgers with balances (§16.4.2). A read. */
export async function fetchCreditorAccounts(
  accountId: string,
  asAt?: string
): Promise<TrustCreditorAccount[]> {
  const qs = asAt ? `?asAt=${encodeURIComponent(asAt)}` : "";
  return api.get<TrustCreditorAccount[]>(`/api/trust-accounts/${accountId}/creditor-accounts${qs}`);
}

/** One creditor ledger's rows with running balances (§16.4.2, 647C), newest first. A read. */
export async function fetchCreditorLedgerRows(
  creditorAccountId: string,
  params: { page?: number; size?: number } = {}
): Promise<CreditorLedgerPage> {
  const qs = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 25),
  }).toString();
  return api.get<CreditorLedgerPage>(
    `/api/trust-creditor-accounts/${creditorAccountId}/transactions?${qs}`
  );
}
