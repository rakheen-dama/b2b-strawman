"use client";

import type { InvoiceResponse, PaymentEvent } from "@/lib/types";
import { DetailPage } from "@/components/layout/detail-page";
import { InvoiceDetailHeader } from "./invoice-detail-header";
import { LineItemEditor } from "./line-item-editor";
import { InvoicePreview } from "./invoice-preview";
import { PaymentHistory } from "./payment-history";

interface InvoiceDetailClientProps {
  invoice: InvoiceResponse;
  payments: PaymentEvent[];
  orgSlug: string;
  onApproveAction?: () => Promise<void>;
  onSendAction?: () => Promise<void>;
  onVoidAction?: () => Promise<void>;
  onRecordPaymentAction?: () => Promise<void>;
  onAddLineAction?: (data: {
    description: string;
    quantity: number;
    unitPrice: number;
  }) => Promise<void>;
  onUpdateLineAction?: (
    lineId: string,
    data: { description: string; quantity: number; unitPrice: number },
  ) => Promise<void>;
  onDeleteLineAction?: (lineId: string) => Promise<void>;
}

export function InvoiceDetailClient({
  invoice,
  payments,
  orgSlug,
  onApproveAction,
  onSendAction,
  onVoidAction,
  onRecordPaymentAction,
  onAddLineAction,
  onUpdateLineAction,
  onDeleteLineAction,
}: InvoiceDetailClientProps) {
  const isEditable = invoice.status === "DRAFT";

  return (
    <DetailPage
      header={
        <InvoiceDetailHeader
          invoice={invoice}
          orgSlug={orgSlug}
          onApprove={onApproveAction}
          onSend={onSendAction}
          onVoid={onVoidAction}
          onRecordPayment={onRecordPaymentAction}
        />
      }
      tabs={[
        {
          id: "lines",
          label: "Line Items",
          count: invoice.lines.length,
          content: (
            <LineItemEditor
              lines={invoice.lines}
              currency={invoice.currency}
              subtotal={invoice.subtotal}
              taxAmount={invoice.taxAmount}
              total={invoice.total}
              isEditable={isEditable}
              taxBreakdown={invoice.taxBreakdown}
              onAddLine={
                isEditable && onAddLineAction
                  ? onAddLineAction
                  : undefined
              }
              onUpdateLine={
                isEditable && onUpdateLineAction
                  ? onUpdateLineAction
                  : undefined
              }
              onDeleteLine={
                isEditable && onDeleteLineAction
                  ? onDeleteLineAction
                  : undefined
              }
            />
          ),
        },
        {
          id: "preview",
          label: "Preview",
          content: <InvoicePreview invoice={invoice} />,
        },
        {
          id: "payments",
          label: "Payments",
          count: payments.length,
          content: (
            <PaymentHistory
              payments={payments}
              currency={invoice.currency}
            />
          ),
        },
      ]}
      defaultTab="lines"
    />
  );
}
