"use server";

import { api } from "@/lib/api";
import type { TrustCreditorAccount } from "@/lib/types";

/** The trust account's non-client creditor ledgers with balances (§16.4.2). A read. */
export async function fetchCreditorAccounts(
  accountId: string,
  asAt?: string
): Promise<TrustCreditorAccount[]> {
  const qs = asAt ? `?asAt=${encodeURIComponent(asAt)}` : "";
  return api.get<TrustCreditorAccount[]>(`/api/trust-accounts/${accountId}/creditor-accounts${qs}`);
}
