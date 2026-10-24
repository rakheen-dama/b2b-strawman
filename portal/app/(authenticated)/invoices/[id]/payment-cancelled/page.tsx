"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, XCircle } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { Skeleton } from "@b2mash/ui/skeleton";
import { useBranding } from "@/hooks/use-branding";
import { partPaymentContactMessage } from "@/lib/utils";
import type { PortalInvoiceDetail } from "@/lib/types";

export default function PaymentCancelledPage() {
  const params = useParams();
  const invoiceId = Array.isArray(params.id) ? params.id[0] : (params.id ?? "");
  const { orgName } = useBranding();

  const [invoice, setInvoice] = useState<PortalInvoiceDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function fetchInvoice() {
      try {
        const data = await portalGet<PortalInvoiceDetail>(
          `/portal/invoices/${invoiceId}`,
        );
        if (!cancelled) {
          setInvoice(data);
        }
      } catch {
        // Silently handle — page still renders without pay button
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchInvoice();

    return () => {
      cancelled = true;
    };
  }, [invoiceId]);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-1/3" />
        <Skeleton className="h-4 w-2/3" />
      </div>
    );
  }

  // Retry is SENT-only, like Pay Now below; a part-paid note's balance goes via the firm
  // (R-3/R-4). Other statuses (or a failed fetch) get no follow-up sentence.
  const followUp =
    invoice?.status === "SENT"
      ? "You can try again when you are ready."
      : invoice?.status === "PARTIALLY_PAID"
        ? partPaymentContactMessage(invoice, orgName)
        : null;

  return (
    <div className="space-y-8">
      {/* Back link */}
      <Link
        href={`/invoices/${invoiceId}`}
        className="inline-flex min-h-11 items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Back to fee note
      </Link>

      <div className="mx-auto max-w-lg text-center">
        <div className="space-y-4">
          <div className="flex justify-center">
            <XCircle className="size-12 text-red-500" />
          </div>
          <h1 className="font-display text-2xl font-semibold text-slate-900">
            Payment was cancelled
          </h1>
          <p
            data-testid="payment-cancelled-message"
            className="text-sm text-slate-600"
          >
            {followUp
              ? `Your payment was not completed. ${followUp}`
              : "Your payment was not completed."}
          </p>
        </div>

        <div className="mt-8 flex flex-col items-stretch gap-3 sm:items-center">
          {/* SENT only: a PARTIALLY_PAID note's paymentUrl is the cancelled full-amount checkout (G88-M11) */}
          {invoice?.status === "SENT" && invoice.paymentUrl && (
            <a
              href={invoice.paymentUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 sm:w-auto"
            >
              Pay Now
            </a>
          )}
          <Link
            href={`/invoices/${invoiceId}`}
            className="inline-flex min-h-11 items-center justify-center text-sm text-slate-500 hover:text-slate-700"
          >
            View Fee Note
          </Link>
        </div>
      </div>
    </div>
  );
}
