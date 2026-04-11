"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { InvoiceResponse, RecordPaymentRequest, ValidationCheck } from "@/lib/types";
import { classifyError } from "@/lib/error-handler";
import { createMessages } from "@/lib/messages";

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
    revalidatePath(`/org/${slug}/customers/${customerId}`);
  }
}

export async function approveInvoice(
  slug: string,
  invoiceId: string,
  customerId: string
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can approve invoices." };
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
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can send invoices." };
  }

  try {
    const body = overrideWarnings ? { overrideWarnings: true } : undefined;
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
  request?: RecordPaymentRequest
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return {
      success: false,
      error: "Only admins and owners can record payments.",
    };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(
      `/api/invoices/${invoiceId}/payment`,
      request ?? {}
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

export async function voidInvoice(
  slug: string,
  invoiceId: string,
  customerId: string
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can void invoices." };
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
