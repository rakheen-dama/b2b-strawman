"use server";

import { revalidatePath } from "next/cache";
import { api } from "@/lib/api";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import type { TrustTransaction } from "@/lib/types";
import type {
  RecordDepositFormData,
  RecordPaymentFormData,
  RecordTransferFormData,
  RecordFeeTransferFormData,
  RecordRefundFormData,
} from "@/lib/schemas/trust";

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

export interface TransactionPage {
  content: TrustTransaction[];
  totalElements: number;
  totalPages: number;
  pageSize: number;
  pageNumber: number;
}

interface ActionResult {
  success: boolean;
  error?: string;
}

// ── Capability helpers ────────────────────────────────────────────

async function requireManageTrust(): Promise<ActionResult | null> {
  const caps = await fetchMyCapabilities();
  if (
    !caps.isAdmin &&
    !caps.isOwner &&
    !caps.capabilities.includes("MANAGE_TRUST")
  ) {
    return { success: false, error: "Forbidden" };
  }
  return null;
}

async function requireApproveTrust(): Promise<ActionResult | null> {
  const caps = await fetchMyCapabilities();
  if (
    !caps.isAdmin &&
    !caps.isOwner &&
    !caps.capabilities.includes("APPROVE_TRUST_PAYMENT")
  ) {
    return { success: false, error: "Forbidden" };
  }
  return null;
}

function revalidateTrust(slug: string) {
  revalidatePath(`/org/${slug}/trust-accounting`);
  revalidatePath(`/org/${slug}/trust-accounting/transactions`);
}

// ── Fetch actions ─────────────────────────────────────────────────

export async function fetchTransactions(
  accountId: string,
  params: {
    dateFrom?: string;
    dateTo?: string;
    type?: string;
    status?: string;
    customerId?: string;
    projectId?: string;
    page?: number;
    size?: number;
  } = {},
): Promise<TransactionPage> {
  const queryParams = new URLSearchParams();
  if (params.dateFrom) queryParams.set("dateFrom", params.dateFrom);
  if (params.dateTo) queryParams.set("dateTo", params.dateTo);
  if (params.type) queryParams.set("type", params.type);
  if (params.status) queryParams.set("status", params.status);
  if (params.customerId) queryParams.set("customerId", params.customerId);
  if (params.projectId) queryParams.set("projectId", params.projectId);
  queryParams.set("page", String(params.page ?? 0));
  queryParams.set("size", String(params.size ?? 20));
  queryParams.set("sort", "transactionDate,desc");

  const qs = queryParams.toString();
  const result = await api.get<PaginatedResponse<TrustTransaction>>(
    `/api/trust-accounts/${accountId}/transactions${qs ? `?${qs}` : ""}`,
  );

  return {
    content: result.content,
    totalElements: result.page.totalElements,
    totalPages: result.page.totalPages,
    pageSize: result.page.size,
    pageNumber: result.page.number,
  };
}

// ── Record actions ────────────────────────────────────────────────

export async function recordDeposit(
  accountId: string,
  slug: string,
  data: RecordDepositFormData,
): Promise<ActionResult> {
  const denied = await requireManageTrust();
  if (denied) return denied;

  try {
    await api.post(`/api/trust-accounts/${accountId}/transactions/deposit`, {
      customerId: data.customerId,
      projectId: data.projectId || null,
      amount: data.amount,
      reference: data.reference,
      description: data.description || null,
      transactionDate: data.transactionDate,
    });
    revalidateTrust(slug);
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error ? error.message : "Failed to record deposit",
    };
  }
}

export async function recordPayment(
  accountId: string,
  slug: string,
  data: RecordPaymentFormData,
): Promise<ActionResult> {
  const denied = await requireManageTrust();
  if (denied) return denied;

  try {
    await api.post(`/api/trust-accounts/${accountId}/transactions/payment`, {
      customerId: data.customerId,
      projectId: data.projectId || null,
      amount: data.amount,
      reference: data.reference,
      description: data.description || null,
      transactionDate: data.transactionDate,
    });
    revalidateTrust(slug);
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error ? error.message : "Failed to record payment",
    };
  }
}

export async function recordTransfer(
  accountId: string,
  slug: string,
  data: RecordTransferFormData,
): Promise<ActionResult> {
  const denied = await requireManageTrust();
  if (denied) return denied;

  try {
    await api.post(`/api/trust-accounts/${accountId}/transactions/transfer`, {
      sourceCustomerId: data.sourceCustomerId,
      targetCustomerId: data.targetCustomerId,
      projectId: data.projectId || null,
      amount: data.amount,
      reference: data.reference,
      description: data.description || null,
      transactionDate: data.transactionDate,
    });
    revalidateTrust(slug);
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error ? error.message : "Failed to record transfer",
    };
  }
}

export async function recordFeeTransfer(
  accountId: string,
  slug: string,
  data: RecordFeeTransferFormData,
): Promise<ActionResult> {
  const denied = await requireManageTrust();
  if (denied) return denied;

  try {
    await api.post(
      `/api/trust-accounts/${accountId}/transactions/fee-transfer`,
      {
        customerId: data.customerId,
        invoiceId: data.invoiceId,
        amount: data.amount,
        reference: data.reference,
      },
    );
    revalidateTrust(slug);
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to record fee transfer",
    };
  }
}

export async function recordRefund(
  accountId: string,
  slug: string,
  data: RecordRefundFormData,
): Promise<ActionResult> {
  const denied = await requireManageTrust();
  if (denied) return denied;

  try {
    await api.post(`/api/trust-accounts/${accountId}/transactions/refund`, {
      customerId: data.customerId,
      amount: data.amount,
      reference: data.reference,
      description: data.description || null,
      transactionDate: data.transactionDate,
    });
    revalidateTrust(slug);
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error ? error.message : "Failed to record refund",
    };
  }
}

// ── Approval actions ──────────────────────────────────────────────

export async function approveTransaction(
  transactionId: string,
): Promise<ActionResult> {
  const denied = await requireApproveTrust();
  if (denied) return denied;

  try {
    await api.post(`/api/trust-transactions/${transactionId}/approve`);
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to approve transaction",
    };
  }
}

export async function rejectTransaction(
  transactionId: string,
  reason: string,
): Promise<ActionResult> {
  const denied = await requireApproveTrust();
  if (denied) return denied;

  try {
    await api.post(`/api/trust-transactions/${transactionId}/reject`, {
      reason,
    });
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to reject transaction",
    };
  }
}

export async function reverseTransaction(
  transactionId: string,
  reason: string,
): Promise<ActionResult> {
  const denied = await requireManageTrust();
  if (denied) return denied;

  try {
    await api.post(`/api/trust-transactions/${transactionId}/reverse`, {
      reason,
    });
    revalidatePath("/", "layout");
    return { success: true };
  } catch (error) {
    return {
      success: false,
      error:
        error instanceof Error
          ? error.message
          : "Failed to reverse transaction",
    };
  }
}
