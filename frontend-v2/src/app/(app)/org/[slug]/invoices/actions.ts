"use server";

import {
  createInvoiceDraft,
  approveInvoice,
  sendInvoice,
  voidInvoice,
  recordPayment,
  addLineItem,
  updateLineItem,
  deleteLineItem,
} from "@/lib/api/invoices";
import { revalidatePath } from "next/cache";

export async function createInvoiceDraftAction(data: {
  customerId: string;
  currency: string;
  dueDate?: string;
}): Promise<{ id: string } | { error: string }> {
  try {
    const invoice = await createInvoiceDraft({
      customerId: data.customerId,
      currency: data.currency,
      timeEntryIds: [],
      dueDate: data.dueDate,
    });
    return { id: invoice.id };
  } catch (error) {
    return { error: error instanceof Error ? error.message : "Failed to create invoice" };
  }
}

export async function approveInvoiceAction(invoiceId: string): Promise<void> {
  await approveInvoice(invoiceId);
  revalidatePath("/", "layout");
}

export async function sendInvoiceAction(invoiceId: string): Promise<void> {
  await sendInvoice(invoiceId);
  revalidatePath("/", "layout");
}

export async function voidInvoiceAction(invoiceId: string): Promise<void> {
  await voidInvoice(invoiceId);
  revalidatePath("/", "layout");
}

export async function recordPaymentAction(invoiceId: string): Promise<void> {
  await recordPayment(invoiceId);
  revalidatePath("/", "layout");
}

export async function addLineItemAction(
  invoiceId: string,
  data: { description: string; quantity: number; unitPrice: number },
): Promise<void> {
  await addLineItem(invoiceId, data);
  revalidatePath("/", "layout");
}

export async function updateLineItemAction(
  invoiceId: string,
  lineId: string,
  data: { description: string; quantity: number; unitPrice: number },
): Promise<void> {
  await updateLineItem(invoiceId, lineId, data);
  revalidatePath("/", "layout");
}

export async function deleteLineItemAction(
  invoiceId: string,
  lineId: string,
): Promise<void> {
  await deleteLineItem(invoiceId, lineId);
  revalidatePath("/", "layout");
}
