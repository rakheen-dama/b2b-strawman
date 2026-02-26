"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, XCircle } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { Skeleton } from "@/components/ui/skeleton";
import type { PortalInvoiceDetail } from "@/lib/types";

export default function PaymentCancelledPage() {
  const params = useParams();
  const invoiceId = Array.isArray(params.id) ? params.id[0] : (params.id ?? "");

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

  return (
    <div className="space-y-8">
      {/* Back link */}
      <Link
        href={`/invoices/${invoiceId}`}
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Back to invoice
      </Link>

      <div className="mx-auto max-w-lg text-center">
        <div className="space-y-4">
          <div className="flex justify-center">
            <XCircle className="size-12 text-red-500" />
          </div>
          <h1 className="font-display text-2xl font-semibold text-slate-900">
            Payment was cancelled
          </h1>
          <p className="text-sm text-slate-600">
            Your payment was not completed. You can try again when you are ready.
          </p>
        </div>

        <div className="mt-8 flex flex-col items-center gap-3">
          {invoice?.paymentUrl && (
            <a
              href={invoice.paymentUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
            >
              Pay Now
            </a>
          )}
          <Link
            href={`/invoices/${invoiceId}`}
            className="text-sm text-slate-500 hover:text-slate-700"
          >
            View Invoice
          </Link>
        </div>
      </div>
    </div>
  );
}
