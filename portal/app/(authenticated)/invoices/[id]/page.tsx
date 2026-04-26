"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle, CreditCard, Download } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { formatDate } from "@/lib/format";
import { useBranding } from "@/hooks/use-branding";
import { InvoiceStatusBadge } from "@/components/invoice-status-badge";
import { InvoiceLineTable } from "@/components/invoice-line-table";
import { Skeleton } from "@/components/ui/skeleton";
import { StickyActionBar } from "@/components/ui/sticky-action-bar";
import { useTerminology } from "@/lib/terminology";
import type { PortalInvoiceDetail, PortalDownload } from "@/lib/types";

function PageSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-8 w-1/3" />
      <Skeleton className="h-4 w-2/3" />
      <Skeleton className="h-48" />
      <Skeleton className="h-24" />
    </div>
  );
}

export default function InvoiceDetailPage() {
  const params = useParams();
  const invoiceId = Array.isArray(params.id) ? params.id[0] : (params.id ?? "");
  const { orgName } = useBranding();
  const { t } = useTerminology();

  const [invoice, setInvoice] = useState<PortalInvoiceDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchInvoice = useCallback(async () => {
    setError(null);
    setIsLoading(true);
    try {
      const data = await portalGet<PortalInvoiceDetail>(
        `/portal/invoices/${invoiceId}`,
      );
      setInvoice(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load invoice");
    } finally {
      setIsLoading(false);
    }
  }, [invoiceId]);

  useEffect(() => {
    fetchInvoice();
  }, [fetchInvoice]);

  async function handleDownload() {
    try {
      const data = await portalGet<PortalDownload>(
        `/portal/invoices/${invoiceId}/download`,
      );
      window.open(data.downloadUrl, "_blank");
    } catch {
      alert("Download failed. Please try again.");
    }
  }

  if (isLoading) {
    return <PageSkeleton />;
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        <p className="mb-2">{error}</p>
        <button
          type="button"
          onClick={() => fetchInvoice()}
          className="inline-flex min-h-11 items-center rounded-md bg-white px-3 py-1.5 text-sm font-medium text-red-700 ring-1 ring-red-200 hover:bg-red-100"
        >
          Try again
        </button>
      </div>
    );
  }

  if (!invoice) return null;

  const hasPaymentBar = invoice.status === "SENT" && !!invoice.paymentUrl;

  return (
    <>
      <div className={hasPaymentBar ? "space-y-8 pb-24 md:pb-0" : "space-y-8"}>
        {/* Back link */}
        <Link
          href="/invoices"
          className="inline-flex min-h-11 items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
        >
          <ArrowLeft className="size-4" />
          Back to {t("invoices")}
        </Link>

        {/* Header */}
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-display text-2xl font-semibold text-slate-900">
                {invoice.invoiceNumber}
              </h1>
              <InvoiceStatusBadge status={invoice.status} />
            </div>
            <div className="mt-2 flex flex-wrap gap-x-6 gap-y-1 text-sm text-slate-600">
              <span>Issued: {formatDate(invoice.issueDate)}</span>
              <span>Due: {formatDate(invoice.dueDate)}</span>
            </div>
          </div>
          <button
            onClick={handleDownload}
            className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 md:min-h-9 md:w-auto"
            aria-label={`Download ${invoice.invoiceNumber} as PDF`}
          >
            <Download className="size-4" />
            Download PDF
          </button>
        </div>

        {/* Payment Action */}
        {invoice.status === "PAID" && (
          <div className="flex items-center gap-3 rounded-lg border border-green-200 bg-green-50 p-4">
            <CheckCircle className="size-5 text-green-600" />
            <p className="text-sm font-medium text-green-700">
              This {t("invoice")} has been paid
            </p>
          </div>
        )}
        {hasPaymentBar && (
          <div
            data-testid="invoice-payment-inline-banner"
            className="hidden items-center gap-4 rounded-lg border border-teal-200 bg-teal-50 p-4 md:flex"
          >
            <CreditCard className="size-5 text-teal-600" />
            <div className="flex-1">
              <p className="text-sm font-medium text-slate-700">
                Ready to pay? Complete your payment securely online.
              </p>
            </div>
            <a
              href={invoice.paymentUrl!}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-9 items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
            >
              Pay Now
            </a>
          </div>
        )}
        {invoice.status === "SENT" && !invoice.paymentUrl && (
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <p className="text-sm text-slate-600">
              Contact {orgName} to arrange payment
            </p>
          </div>
        )}

        {/* Tax Registration */}
        {invoice.taxRegistrationNumber && (
          <div className="text-sm text-slate-600">
            <span className="font-medium">
              {invoice.taxRegistrationLabel || "Tax Registration"}:
            </span>{" "}
            {invoice.taxRegistrationNumber}
          </div>
        )}

        {/* Line Items */}
        <section>
          <h2 className="font-display mb-4 text-lg font-semibold text-slate-900">
            Line Items
          </h2>
          <InvoiceLineTable
            lines={invoice.lines}
            currency={invoice.currency}
            subtotal={invoice.subtotal}
            taxAmount={invoice.taxAmount}
            total={invoice.total}
            hasPerLineTax={invoice.hasPerLineTax}
            taxBreakdown={invoice.taxBreakdown}
            taxLabel={invoice.taxLabel}
            taxInclusive={invoice.taxInclusive}
          />
        </section>

        {/* Notes */}
        {invoice.notes && (
          <section>
            <h2 className="font-display mb-2 text-lg font-semibold text-slate-900">
              Notes
            </h2>
            <p className="text-sm text-slate-600">{invoice.notes}</p>
          </section>
        )}
      </div>

      {/* Mobile sticky payment bar */}
      {hasPaymentBar && (
        <StickyActionBar>
          <a
            href={invoice.paymentUrl!}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex min-h-11 flex-1 items-center justify-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
          >
            <CreditCard className="size-4" />
            Pay Now
          </a>
        </StickyActionBar>
      )}
    </>
  );
}
