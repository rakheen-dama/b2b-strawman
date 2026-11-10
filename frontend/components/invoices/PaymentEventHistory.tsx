"use client";

import { useState } from "react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  downloadPaymentReceiptAction,
  generatePaymentReceiptAction,
} from "@/app/(app)/org/[slug]/invoices/invoice-payment-actions";
import { useCapabilities } from "@/lib/capabilities";
import { formatDate, formatCurrency } from "@/lib/format";
import type { PaymentEvent, PaymentEventStatus, PaymentMethod } from "@/lib/types";

const STATUS_BADGE: Record<
  PaymentEventStatus,
  {
    label: string;
    variant: "success" | "warning" | "destructive" | "neutral" | "lead";
  }
> = {
  CREATED: { label: "Created", variant: "neutral" },
  PENDING: { label: "Pending", variant: "lead" },
  COMPLETED: { label: "Completed", variant: "success" },
  FAILED: { label: "Failed", variant: "destructive" },
  EXPIRED: { label: "Expired", variant: "warning" },
  CANCELLED: { label: "Cancelled", variant: "neutral" },
};

const PROVIDER_LABELS: Record<string, string> = {
  stripe: "Stripe",
  payfast: "PayFast",
  manual: "Manual",
  noop: "Manual",
  trust_fee_transfer: "Trust transfer",
  xero: "Xero",
};

const METHOD_LABELS: Record<PaymentMethod, string> = {
  EFT: "EFT",
  CARD: "Card",
  CASH: "Cash",
  TRUST_TRANSFER: "Trust transfer",
  XERO: "Xero",
  OTHER: "Other",
};

interface PaymentEventHistoryProps {
  events: PaymentEvent[];
  invoiceId: string;
  slug: string;
}

interface PaymentReceiptCellProps {
  event: PaymentEvent;
  invoiceId: string;
  slug: string;
}

/** "INV-0001/R1" → "INV-0001-R1.pdf" (a slash is not allowed in a file name). */
function receiptFileName(event: PaymentEvent): string {
  return `${(event.receiptNumber ?? `receipt-${event.id}`).replace(/\//g, "-")}.pdf`;
}

function saveBase64Pdf(pdfBase64: string, fileName: string) {
  const byteCharacters = atob(pdfBase64);
  const bytes = new Uint8Array(byteCharacters.length);
  for (let i = 0; i < byteCharacters.length; i++) {
    bytes[i] = byteCharacters.charCodeAt(i);
  }
  const url = URL.createObjectURL(new Blob([bytes], { type: "application/pdf" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/**
 * Phase 89 632C (G88-F32): the receipt for one payment. A COMPLETED payment with a receipt document
 * links it ("Receipt INV-0001/R1"); without one (pre-receipt payments, or the document was deleted —
 * the FK is ON DELETE SET NULL while the number stays) INVOICING holders can generate it. Keyed off
 * receiptGeneratedDocumentId, never receiptNumber.
 */
function PaymentReceiptCell({ event, invoiceId, slug }: PaymentReceiptCellProps) {
  const { hasCapability } = useCapabilities();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDownload(generatedDocumentId: string) {
    setBusy(true);
    setError(null);
    try {
      const result = await downloadPaymentReceiptAction(generatedDocumentId);
      if (result.success && result.pdfBase64) {
        saveBase64Pdf(result.pdfBase64, receiptFileName(event));
      } else {
        setError(result.error ?? "Failed to download the receipt.");
      }
    } catch {
      setError("Failed to download the receipt.");
    } finally {
      setBusy(false);
    }
  }

  async function handleGenerate() {
    setBusy(true);
    setError(null);
    try {
      const result = await generatePaymentReceiptAction(slug, invoiceId, event.id);
      if (!result.success) {
        setError(result.error ?? "Failed to generate the receipt.");
      }
    } catch {
      setError("Failed to generate the receipt.");
    } finally {
      setBusy(false);
    }
  }

  const documentId = event.receiptGeneratedDocumentId;
  const canGenerate = !documentId && hasCapability("INVOICING");
  if (event.status !== "COMPLETED" || (!documentId && !canGenerate)) {
    return <span className="text-slate-400 dark:text-slate-500">{"—"}</span>;
  }

  return (
    <>
      {documentId ? (
        <Button
          type="button"
          variant="link"
          size="xs"
          className="h-auto px-0 text-teal-700 dark:text-teal-400"
          disabled={busy}
          onClick={() => handleDownload(documentId)}
          data-testid={`payment-receipt-link-${event.id}`}
        >
          {event.receiptNumber ? `Receipt ${event.receiptNumber}` : "Receipt"}
        </Button>
      ) : (
        <Button
          type="button"
          variant="outline"
          size="xs"
          disabled={busy}
          onClick={handleGenerate}
          data-testid={`payment-receipt-generate-${event.id}`}
        >
          {busy ? "Generating…" : "Generate receipt"}
        </Button>
      )}
      {error && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{error}</p>}
    </>
  );
}

export function PaymentEventHistory({ events, invoiceId, slug }: PaymentEventHistoryProps) {
  if (!events || events.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
        <h3 className="mb-2 font-semibold text-slate-900 dark:text-slate-100">Payment History</h3>
        <p className="text-sm text-slate-500 dark:text-slate-400">No payment events yet.</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
      <h3 className="mb-3 font-semibold text-slate-900 dark:text-slate-100">Payment History</h3>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="px-3 py-2 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Status
              </th>
              {/* G89-F9: Provider / Method show from xl; below it the 7 columns overflowed */}
              <th className="hidden px-3 py-2 text-left text-xs font-medium tracking-wide text-slate-600 uppercase xl:table-cell dark:text-slate-400">
                Provider
              </th>
              <th className="hidden px-3 py-2 text-left text-xs font-medium tracking-wide text-slate-600 uppercase xl:table-cell dark:text-slate-400">
                Method
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Reference
              </th>
              <th className="px-3 py-2 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Amount
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Date
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Receipt
              </th>
            </tr>
          </thead>
          <tbody>
            {events.map((event) => {
              const badge = STATUS_BADGE[event.status] ?? STATUS_BADGE.CREATED;
              const providerLabel = PROVIDER_LABELS[event.providerSlug] ?? event.providerSlug;
              const methodLabel = event.paymentMethod
                ? (METHOD_LABELS[event.paymentMethod] ?? event.paymentMethod)
                : "\u2014";
              return (
                <tr
                  key={event.id}
                  className="border-b border-slate-100 last:border-0 dark:border-slate-800/50"
                >
                  <td className="px-3 py-2">
                    <Badge variant={badge.variant}>{badge.label}</Badge>
                  </td>
                  <td className="hidden px-3 py-2 text-sm text-slate-600 xl:table-cell dark:text-slate-400">
                    {providerLabel}
                  </td>
                  <td className="hidden px-3 py-2 text-sm text-slate-600 xl:table-cell dark:text-slate-400">
                    {methodLabel}
                  </td>
                  <td className="px-3 py-2 font-mono text-sm text-slate-600 dark:text-slate-400">
                    {event.paymentReference ?? "\u2014"}
                    {/* Below xl the Provider / Method columns are hidden; they ride here */}
                    <div
                      className="mt-0.5 font-sans text-xs text-slate-500 xl:hidden dark:text-slate-400"
                      data-testid="payment-provider-method-compact"
                    >
                      {`${providerLabel} \u00b7 ${methodLabel}`}
                    </div>
                  </td>
                  <td className="px-3 py-2 text-right text-sm text-slate-900 dark:text-slate-100">
                    {formatCurrency(event.amount, event.currency)}
                  </td>
                  <td className="px-3 py-2 text-sm text-slate-600 dark:text-slate-400">
                    {formatDate(event.createdAt)}
                  </td>
                  <td className="px-3 py-2 text-sm">
                    <PaymentReceiptCell event={event} invoiceId={invoiceId} slug={slug} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
