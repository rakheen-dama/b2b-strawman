"use server";

import { revalidatePath } from "next/cache";
import { api } from "@/lib/api";
import type {
  AllocateUnidentifiedRequest,
  CreditorLedgerPage,
  PayToFundRequest,
  TrustCreditorAccount,
  UnidentifiedDepositPage,
  UnidentifiedDepositStatus,
} from "@/lib/types";
import type { AllocateUnidentifiedFormData, PayToFundFormData } from "@/lib/schemas/trust";

interface ActionResult {
  success: boolean;
  error?: string;
}

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

/** The trust account's unidentified deposits, oldest first, with their age (§16.4.2, 647B). A read. */
export async function fetchUnidentifiedDeposits(
  accountId: string,
  params: { page?: number; size?: number; status?: UnidentifiedDepositStatus } = {}
): Promise<UnidentifiedDepositPage> {
  const qs = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 25),
  });
  if (params.status) qs.set("status", params.status);
  return api.get<UnidentifiedDepositPage>(
    `/api/trust-accounts/${accountId}/unidentified-deposits?${qs.toString()}`
  );
}

/**
 * Requests an allocation of an unidentified deposit to a client and matter (647D, §16.3.6): a
 * pending entry another member must approve. A refusal (e.g. a pending resolution, a matter not
 * linked to the client, a date outside the window) comes back as `error`.
 */
export async function allocateUnidentifiedDeposit(
  depositId: string,
  data: AllocateUnidentifiedFormData
): Promise<ActionResult> {
  const body: AllocateUnidentifiedRequest = {
    clientId: data.customerId,
    matterId: data.projectId,
    allocationDate: data.allocationDate || null,
    notes: data.notes || null,
  };
  try {
    await api.post(`/api/unidentified-deposits/${depositId}/allocate`, body);
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Failed to request the allocation",
    };
  }
}

/**
 * Requests payment of an unidentified deposit to the Legal Practitioners' Fidelity Fund (647D,
 * §16.3.6): a pending payment another member must approve. A refusal comes back as `error`.
 */
export async function payUnidentifiedDepositToFund(
  depositId: string,
  data: PayToFundFormData
): Promise<ActionResult> {
  const body: PayToFundRequest = {
    reference: data.reference.trim(),
    transactionDate: data.transactionDate,
    notes: data.notes || null,
  };
  try {
    await api.post(`/api/unidentified-deposits/${depositId}/pay-to-fund`, body);
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Failed to request the payment to the Fund",
    };
  }
}
