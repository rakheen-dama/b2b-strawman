"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import { settle } from "@/lib/settle";
import type { WidgetResult } from "@/lib/dashboard-home";
import { revalidatePath } from "next/cache";
import type {
  InvoiceResponse,
  OutstandingSummary,
  UpdateInvoiceRequest,
  AddLineItemRequest,
  UpdateLineItemRequest,
} from "@/lib/types";
import { classifyError } from "@/lib/error-handler";
import { createMessages } from "@/lib/messages";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface InvoiceActionResult {
  success: boolean;
  error?: string;
  invoice?: InvoiceResponse;
}

function revalidateInvoicePaths(slug: string, invoiceId: string, customerId?: string) {
  revalidatePath(`/org/${slug}/invoices`);
  revalidatePath(`/org/${slug}/invoices/${invoiceId}`);
  if (customerId) {
    revalidatePath(`/org/${slug}/clients/${customerId}`);
  }
}

export async function fetchInvoice(invoiceId: string): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can view fee notes." };
  }

  try {
    const invoice = await api.get<InvoiceResponse>(`/api/invoices/${invoiceId}`);
    return { success: true, invoice };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }
}

export async function fetchInvoices(
  status?: string,
  customerId?: string
): Promise<{ success: boolean; error?: string; invoices?: InvoiceResponse[] }> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can view fee notes." };
  }

  try {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (customerId) params.set("clientId", customerId);
    const qs = params.toString();
    const url = `/api/invoices${qs ? `?${qs}` : ""}`;
    const invoices = await api.get<InvoiceResponse[]>(url);
    return { success: true, invoices };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }
}

/**
 * Fee notes page tiles (Phase 88 U3, §13.4.3): the debtor-book definition, in the org default
 * currency. The summary is gated on FINANCIAL_VISIBILITY while the page is gated on INVOICING, so a
 * 403 (or 404) is "absent", not an error — the page drops the tiles instead of crashing. Same
 * `settle` as Home's widgets: 401 → branded re-login, anything else → "error".
 */
export async function fetchOutstandingSummaryAction(): Promise<WidgetResult<OutstandingSummary>> {
  return settle(true, "outstanding summary", () =>
    api.get<OutstandingSummary>("/api/invoices/outstanding-summary")
  );
}

export async function updateInvoice(
  slug: string,
  invoiceId: string,
  customerId: string,
  request: UpdateInvoiceRequest
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can update fee notes." };
  }

  try {
    const invoice = await api.put<InvoiceResponse>(`/api/invoices/${invoiceId}`, request);
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

export async function deleteInvoice(
  slug: string,
  invoiceId: string,
  customerId: string
): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can delete fee notes." };
  }

  try {
    await api.delete(`/api/invoices/${invoiceId}`);
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }
}

export async function addLineItem(
  slug: string,
  invoiceId: string,
  customerId: string,
  request: AddLineItemRequest
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can add line items." };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(`/api/invoices/${invoiceId}/lines`, request);
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

export async function addDisbursementLines(
  slug: string,
  invoiceId: string,
  customerId: string,
  disbursementIds: string[]
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return {
      success: false,
      error: "Only admins and owners can add disbursement lines.",
    };
  }
  if (!disbursementIds || disbursementIds.length === 0) {
    return { success: false, error: "No disbursements selected." };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(
      `/api/invoices/${invoiceId}/disbursement-lines`,
      { disbursementIds }
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

export async function updateLineItem(
  slug: string,
  invoiceId: string,
  lineId: string,
  customerId: string,
  request: UpdateLineItemRequest
): Promise<InvoiceActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return {
      success: false,
      error: "Only admins and owners can update line items.",
    };
  }

  try {
    const invoice = await api.put<InvoiceResponse>(
      `/api/invoices/${invoiceId}/lines/${lineId}`,
      request
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

export async function deleteLineItem(
  slug: string,
  invoiceId: string,
  lineId: string,
  customerId: string
): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return {
      success: false,
      error: "Only admins and owners can delete line items.",
    };
  }

  try {
    await api.delete(`/api/invoices/${invoiceId}/lines/${lineId}`);
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : createMessages("errors").t(classifyError(error).messageCode);
    return { success: false, error: message };
  }
}
