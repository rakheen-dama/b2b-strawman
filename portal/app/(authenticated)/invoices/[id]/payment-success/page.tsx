"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle, Clock, AlertTriangle } from "lucide-react";
import { usePaymentStatus } from "@/hooks/use-payment-status";
import { formatDate } from "@/lib/format";

export default function PaymentSuccessPage() {
  const params = useParams();
  const invoiceId = Array.isArray(params.id) ? params.id[0] : (params.id ?? "");
  const { status, paidAt, isPolling, isTimeout } = usePaymentStatus(invoiceId);

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
        {/* Polling state */}
        {isPolling && !isTimeout && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <Clock className="size-12 text-teal-600 animate-pulse" />
            </div>
            <h1 className="font-display text-2xl font-semibold text-slate-900">
              Payment is being processed...
            </h1>
            <p className="text-sm text-slate-600">
              Please wait while we confirm your payment. This usually takes a few seconds.
            </p>
          </div>
        )}

        {/* Success state */}
        {!isPolling && status === "PAID" && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <CheckCircle className="size-12 text-green-600" />
            </div>
            <h1 className="font-display text-2xl font-semibold text-slate-900">
              Payment confirmed
            </h1>
            <p className="text-sm text-slate-600">
              Payment received — thank you!
            </p>
            {paidAt && (
              <p className="text-sm text-slate-500">
                Paid on {formatDate(paidAt)}
              </p>
            )}
          </div>
        )}

        {/* Timeout state — only if not yet confirmed PAID */}
        {isTimeout && status !== "PAID" && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <AlertTriangle className="size-12 text-amber-500" />
            </div>
            <h1 className="font-display text-2xl font-semibold text-slate-900">
              Payment is still being processed
            </h1>
            <p className="text-sm text-slate-600">
              Your payment is taking longer than expected. Check back later for confirmation.
            </p>
          </div>
        )}

        {/* Always show link back */}
        <div className="mt-8">
          <Link
            href={`/invoices/${invoiceId}`}
            className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
          >
            View Invoice
          </Link>
        </div>
      </div>
    </div>
  );
}
