"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { InvoiceResponse, RecordPaymentRequest, ValidationCheck } from "@/lib/types";
import { classifyError } from "@/lib/error-handler";
import { createMessages } from "@/lib/messages";
import { downloadGeneratedDocumentAction } from "@/app/(app)/org/[slug]/settings/documents/_tabs/template-generation-actions";

interface InvoiceActionResult {
  success: boolean;
  error?: string;
  invoice?: InvoiceResponse;
  canOverride?: boolean;
  validationChecks?: ValidationCheck[];
}

function revalidateInvoicePaths(slug: string, invoiceId: string, customerId?: string) {
  revalidatePath(`/org/${slug}/invoices`);
  revalidatePath(`/org/${slug}/invoices/${invoiceId}`);
  if (customerId) {
    revalidatePath(`/org/${slug}/clients/${customerId}`);
  }
}

export async function approveInvoice(
  slug: string,
  invoiceId: string,
  customerId: string
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can approve fee notes." };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(`/api/invoices/${invoiceId}/approve`);
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }
}

export async function sendInvoice(
  slug: string,
  invoiceId: string,
  customerId: string,
  overrideWarnings?: boolean
): Promise<InvoiceActionResult> {
  try {
    const caps = await fetchMyCapabilities();
    if (!caps.isAdmin && !caps.isOwner) {
      return { success: false, error: "Only admins and owners can send fee notes." };
    }

    const body = overrideWarnings ? { overrideWarnings: true } : { overrideWarnings: false };
    const invoice = await api.post<InvoiceResponse>(`/api/invoices/${invoiceId}/send`, body);
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      // Check for 422 with canOverride flag
      if (error.status === 422 && error.detail?.canOverride) {
        return {
          success: false,
          error: error.message,
          canOverride: true,
          validationChecks: error.detail.validationChecks as ValidationCheck[] | undefined,
        };
      }
      return { success: false, error: error.message };
    }
    const classified = classifyError(error);
    return {
      success: false,
      error: createMessages("errors").t(classified.messageCode),
    };
  }
}

export async function recordPayment(
  slug: string,
  invoiceId: string,
  customerId: string,
  request: RecordPaymentRequest
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return {
      success: false,
      error: "Only admins and owners can record payments.",
    };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(`/api/invoices/${invoiceId}/payment`, request);
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      // 409 "Payment exceeds balance due": ApiError.message is only the ProblemDetail detail
      // ("Balance due is R 37 182,00"), so lead with the title the user must see.
      const title = error.detail?.title;
      if (error.status === 409 && title) {
        const detail = error.message && error.message !== title ? ` ${error.message}` : "";
        return { success: false, error: `${title}.${detail}` };
      }
      return { success: false, error: error.message };
    }
    return {
      success: false,
      error: createMessages("errors").t(classifyError(error).messageCode),
    };
  }
}

export async function voidInvoice(
  slug: string,
  invoiceId: string,
  customerId: string
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can void fee notes." };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(`/api/invoices/${invoiceId}/void`);
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }
}

export async function refreshPaymentLink(
  slug: string,
  invoiceId: string,
  customerId: string
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return {
      success: false,
      error: "Only admins and owners can regenerate payment links.",
    };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(
      `/api/invoices/${invoiceId}/refresh-payment-link`
    );
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }
}

interface PaymentReceiptActionResult {
  success: boolean;
  error?: string;
  generatedDocumentId?: string;
}

/**
 * Phase 89 632C: the receipt for a COMPLETED payment, generated lazily when missing
 * (POST …/payment-events/{id}/receipt, INVOICING; idempotent — an existing receipt is returned).
 * The response carries no receipt number, so the fee-note page is revalidated to re-read the events.
 */
export async function generatePaymentReceiptAction(
  slug: string,
  invoiceId: string,
  paymentEventId: string
): Promise<PaymentReceiptActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner && !caps.capabilities.includes("INVOICING")) {
    return { success: false, error: "You do not have permission to generate receipts." };
  }

  try {
    const { generatedDocumentId } = await api.post<{ generatedDocumentId: string }>(
      `/api/invoices/${invoiceId}/payment-events/${paymentEventId}/receipt`
    );
    revalidatePath(`/org/${slug}/invoices/${invoiceId}`);
    return { success: true, generatedDocumentId };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }
}

/** Phase 89 632C: the receipt PDF (the same download as the Generated Documents list). */
export async function downloadPaymentReceiptAction(generatedDocumentId: string) {
  return downloadGeneratedDocumentAction(generatedDocumentId);
}
