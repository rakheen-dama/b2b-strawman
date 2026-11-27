"use client";

import { useMemo, useState } from "react";
import { CreditCard } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { DataTable } from "@/components/data-table/data-table";
import { DateCell, EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import {
  downloadPaymentReceiptAction,
  generatePaymentReceiptAction,
} from "@/app/(app)/org/[slug]/invoices/invoice-payment-actions";
import { useCapabilities } from "@/lib/capabilities";
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
    return <EmptyCell />;
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

const getPaymentEventRowId = (event: PaymentEvent) => event.id;

function providerLabelOf(event: PaymentEvent): string {
  return PROVIDER_LABELS[event.providerSlug] ?? event.providerSlug;
}

function methodLabelOf(event: PaymentEvent): string {
  return event.paymentMethod
    ? (METHOD_LABELS[event.paymentMethod] ?? event.paymentMethod)
    : "\u2014";
}

/**
 * Payment history on the `DataTable` kit (Phase 91 666B). Already a client module, so the columns
 * are built here (`useMemo` on the receipt context). Provider / Method are `priority: "low"` (shown
 * from a 67rem table frame, G89-F9); below it they ride under the reference in the compact line.
 */
export function PaymentEventHistory({ events, invoiceId, slug }: PaymentEventHistoryProps) {
  const columns = useMemo(
    () =>
      defineColumns<PaymentEvent>([
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => {
            const badge = STATUS_BADGE[row.original.status] ?? STATUS_BADGE.CREATED;
            return <Badge variant={badge.variant}>{badge.label}</Badge>;
          },
        },
        {
          id: "provider",
          meta: { label: "Provider", priority: "low", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => providerLabelOf(row.original),
        },
        {
          id: "method",
          meta: { label: "Method", priority: "low", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => methodLabelOf(row.original),
        },
        {
          id: "reference",
          meta: { label: "Reference", cellClassName: "text-muted-foreground font-mono" },
          cell: ({ row }) => (
            <>
              {row.original.paymentReference ?? "\u2014"}
              {/* Below a 67rem frame the Provider / Method columns are hidden; they ride here */}
              <div
                className="mt-0.5 font-sans text-xs @min-[67rem]:hidden"
                data-testid="payment-provider-method-compact"
              >
                <span className="sr-only">Provider: </span>
                {providerLabelOf(row.original)}
                <span aria-hidden="true">{" \u00b7 "}</span>
                <span className="sr-only">Method: </span>
                {methodLabelOf(row.original)}
              </div>
            </>
          ),
        },
        {
          id: "amount",
          meta: { label: "Amount", align: "end" },
          cell: ({ row }) => (
            <MoneyCell
              amount={row.original.amount}
              currency={row.original.currency}
              className="text-foreground"
            />
          ),
        },
        {
          id: "date",
          meta: { label: "Date", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => <DateCell value={row.original.createdAt} />,
        },
        {
          id: "receipt",
          enableHiding: false,
          meta: { label: "Receipt" },
          cell: ({ row }) => (
            <PaymentReceiptCell event={row.original} invoiceId={invoiceId} slug={slug} />
          ),
        },
      ]),
    [invoiceId, slug]
  );

  return (
    <div className="space-y-3">
      <h3 className="font-display text-foreground text-lg">Payment History</h3>
      <DataTable
        columns={columns}
        data={events}
        getRowId={getPaymentEventRowId}
        empty={
          <EmptyState
            variant="inline"
            icon={CreditCard}
            title="No payment events yet."
            description={null}
          />
        }
        pageSize={null}
        columnVisibility={false}
        stickyHeader={false}
      />
    </div>
  );
}
