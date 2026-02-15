"use server";

import { auth } from "@clerk/nextjs/server";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  InvoiceResponse,
  UpdateInvoiceRequest,
  AddLineItemRequest,
  UpdateLineItemRequest,
  RecordPaymentRequest,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface InvoiceActionResult {
  success: boolean;
  error?: string;
  invoice?: InvoiceResponse;
}

function revalidateInvoicePaths(
  slug: string,
  invoiceId: string,
  customerId?: string,
) {
  revalidatePath(`/org/${slug}/invoices`);
  revalidatePath(`/org/${slug}/invoices/${invoiceId}`);
  if (customerId) {
    revalidatePath(`/org/${slug}/customers/${customerId}`);
  }
}

export async function fetchInvoice(
  invoiceId: string,
): Promise<InvoiceActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can view invoices." };
  }

  try {
    const invoice = await api.get<InvoiceResponse>(`/api/invoices/${invoiceId}`);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch invoice." };
  }
}

export async function fetchInvoices(
  status?: string,
  customerId?: string,
): Promise<{ success: boolean; error?: string; invoices?: InvoiceResponse[] }> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can view invoices." };
  }

  try {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (customerId) params.set("customerId", customerId);
    const qs = params.toString();
    const url = `/api/invoices${qs ? `?${qs}` : ""}`;
    const invoices = await api.get<InvoiceResponse[]>(url);
    return { success: true, invoices };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch invoices." };
  }
}

export async function updateInvoice(
  slug: string,
  invoiceId: string,
  customerId: string,
  request: UpdateInvoiceRequest,
): Promise<InvoiceActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can update invoices." };
  }

  try {
    const invoice = await api.put<InvoiceResponse>(
      `/api/invoices/${invoiceId}`,
      request,
    );
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to update invoice." };
  }
}

export async function deleteInvoice(
  slug: string,
  invoiceId: string,
  customerId: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can delete invoices." };
  }

  try {
    await api.delete(`/api/invoices/${invoiceId}`);
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to delete invoice." };
  }
}

export async function approveInvoice(
  slug: string,
  invoiceId: string,
  customerId: string,
): Promise<InvoiceActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can approve invoices." };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(
      `/api/invoices/${invoiceId}/approve`,
    );
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to approve invoice." };
  }
}

export async function sendInvoice(
  slug: string,
  invoiceId: string,
  customerId: string,
): Promise<InvoiceActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can send invoices." };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(
      `/api/invoices/${invoiceId}/send`,
    );
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to send invoice." };
  }
}

export async function recordPayment(
  slug: string,
  invoiceId: string,
  customerId: string,
  request?: RecordPaymentRequest,
): Promise<InvoiceActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can record payments.",
    };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(
      `/api/invoices/${invoiceId}/payment`,
      request ?? {},
    );
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to record payment." };
  }
}

export async function voidInvoice(
  slug: string,
  invoiceId: string,
  customerId: string,
): Promise<InvoiceActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can void invoices." };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(
      `/api/invoices/${invoiceId}/void`,
    );
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to void invoice." };
  }
}

export async function addLineItem(
  slug: string,
  invoiceId: string,
  customerId: string,
  request: AddLineItemRequest,
): Promise<InvoiceActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can add line items." };
  }

  try {
    const invoice = await api.post<InvoiceResponse>(
      `/api/invoices/${invoiceId}/lines`,
      request,
    );
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to add line item." };
  }
}

export async function updateLineItem(
  slug: string,
  invoiceId: string,
  lineId: string,
  customerId: string,
  request: UpdateLineItemRequest,
): Promise<InvoiceActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can update line items.",
    };
  }

  try {
    const invoice = await api.put<InvoiceResponse>(
      `/api/invoices/${invoiceId}/lines/${lineId}`,
      request,
    );
    revalidateInvoicePaths(slug, invoiceId, customerId);
    return { success: true, invoice };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to update line item." };
  }
}

export async function deleteLineItem(
  slug: string,
  invoiceId: string,
  lineId: string,
  customerId: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to delete line item." };
  }
}
