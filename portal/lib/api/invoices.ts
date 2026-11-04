import { portalGet } from "@/lib/api-client";
import type { PortalDownload, PortalInvoicePayment } from "@/lib/types";

/**
 * Portal fee-note payments API client (Phase 89 632C, OQ #11).
 *
 * Backend (632B): routes live under `/portal/invoices/{id}/payments`.
 *   - `GET /portal/invoices/{id}/payments` → the fee note's COMPLETED payments, oldest first.
 *   - `GET /portal/invoices/{id}/payments/{paymentEventId}/receipt` → a 1 h presigned receipt URL;
 *     the receipt is generated on first download when it does not exist yet.
 * Both answer **404** unless the fee note belongs to the signed-in contact's client and the payment
 * to the fee note (→ `Error("The requested resource was not found.")`).
 */

/** The fee note's completed payments (empty when none). */
export async function listPayments(
  invoiceId: string,
): Promise<PortalInvoicePayment[]> {
  return portalGet<PortalInvoicePayment[]>(
    `/portal/invoices/${encodeURIComponent(invoiceId)}/payments`,
  );
}

/** Presigned receipt download for one payment — open with `window.open(url, "_blank")`. */
export async function receiptDownload(
  invoiceId: string,
  paymentEventId: string,
): Promise<PortalDownload> {
  return portalGet<PortalDownload>(
    `/portal/invoices/${encodeURIComponent(invoiceId)}/payments/${encodeURIComponent(paymentEventId)}/receipt`,
  );
}
