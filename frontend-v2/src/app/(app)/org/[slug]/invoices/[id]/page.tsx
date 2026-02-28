import { fetchInvoice, fetchPaymentEvents } from "@/lib/api/invoices";
import { handleApiError } from "@/lib/api";
import { InvoiceDetailClient } from "@/components/invoices/invoice-detail-client";
import {
  approveInvoiceAction,
  sendInvoiceAction,
  voidInvoiceAction,
  recordPaymentAction,
  addLineItemAction,
  updateLineItemAction,
  deleteLineItemAction,
} from "../actions";

interface InvoiceDetailPageProps {
  params: Promise<{ slug: string; id: string }>;
}

export default async function InvoiceDetailPage({
  params,
}: InvoiceDetailPageProps) {
  const { slug, id } = await params;

  let invoice;
  let payments;
  try {
    [invoice, payments] = await Promise.all([
      fetchInvoice(id),
      fetchPaymentEvents(id),
    ]);
  } catch (error) {
    handleApiError(error);
  }

  // Bind server actions to this invoice ID
  const boundApprove = approveInvoiceAction.bind(null, id);
  const boundSend = sendInvoiceAction.bind(null, id);
  const boundVoid = voidInvoiceAction.bind(null, id);
  const boundRecordPayment = recordPaymentAction.bind(null, id);

  const boundAddLine = async (data: {
    description: string;
    quantity: number;
    unitPrice: number;
  }) => {
    "use server";
    await addLineItemAction(id, data);
  };

  const boundUpdateLine = async (
    lineId: string,
    data: { description: string; quantity: number; unitPrice: number },
  ) => {
    "use server";
    await updateLineItemAction(id, lineId, data);
  };

  const boundDeleteLine = async (lineId: string) => {
    "use server";
    await deleteLineItemAction(id, lineId);
  };

  return (
    <InvoiceDetailClient
      invoice={invoice}
      payments={payments}
      orgSlug={slug}
      onApproveAction={boundApprove}
      onSendAction={boundSend}
      onVoidAction={boundVoid}
      onRecordPaymentAction={boundRecordPayment}
      onAddLineAction={boundAddLine}
      onUpdateLineAction={boundUpdateLine}
      onDeleteLineAction={boundDeleteLine}
    />
  );
}
