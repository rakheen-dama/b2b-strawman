"use client";

import { useState } from "react";
import { Download, Loader2, FileText } from "lucide-react";

import type { InvoiceResponse } from "@/lib/types";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

interface InvoicePreviewProps {
  invoice: InvoiceResponse;
  previewHtml?: string | null;
  onDownloadPdf?: () => Promise<void>;
}

export function InvoicePreview({
  invoice,
  previewHtml,
  onDownloadPdf,
}: InvoicePreviewProps) {
  const [isDownloading, setIsDownloading] = useState(false);

  const handleDownload = async () => {
    if (!onDownloadPdf) return;
    setIsDownloading(true);
    try {
      await onDownloadPdf();
    } finally {
      setIsDownloading(false);
    }
  };

  if (previewHtml) {
    return (
      <div className="space-y-4">
        <div className="flex justify-end">
          {onDownloadPdf && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleDownload}
              disabled={isDownloading}
            >
              {isDownloading ? (
                <Loader2 className="mr-1.5 size-4 animate-spin" />
              ) : (
                <Download className="mr-1.5 size-4" />
              )}
              Download PDF
            </Button>
          )}
        </div>
        <Card className="p-0 overflow-hidden">
          <iframe
            srcDoc={previewHtml}
            className="h-[800px] w-full border-0"
            title="Invoice Preview"
            sandbox="allow-same-origin"
          />
        </Card>
      </div>
    );
  }

  // Fallback: render a simple structured preview from invoice data
  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        {onDownloadPdf && (
          <Button
            variant="outline"
            size="sm"
            onClick={handleDownload}
            disabled={isDownloading}
          >
            {isDownloading ? (
              <Loader2 className="mr-1.5 size-4 animate-spin" />
            ) : (
              <Download className="mr-1.5 size-4" />
            )}
            Download PDF
          </Button>
        )}
      </div>
      <Card className="p-8">
        <div className="space-y-8">
          {/* Header */}
          <div className="flex justify-between">
            <div>
              <h2 className="font-display text-xl font-semibold text-slate-900">
                INVOICE
              </h2>
              <p className="mt-1 font-mono text-sm text-slate-600">
                {invoice.invoiceNumber ?? "Draft"}
              </p>
            </div>
            <div className="text-right text-sm text-slate-600">
              <p className="font-semibold text-slate-900">{invoice.orgName}</p>
            </div>
          </div>

          {/* Bill to + dates */}
          <div className="grid grid-cols-2 gap-8">
            <div>
              <p className="text-xs font-medium uppercase text-slate-500">
                Bill To
              </p>
              <p className="mt-1 text-sm font-medium text-slate-900">
                {invoice.customerName}
              </p>
              {invoice.customerEmail && (
                <p className="text-sm text-slate-600">
                  {invoice.customerEmail}
                </p>
              )}
              {invoice.customerAddress && (
                <p className="whitespace-pre-line text-sm text-slate-600">
                  {invoice.customerAddress}
                </p>
              )}
            </div>
            <div className="text-right text-sm">
              {invoice.issueDate && (
                <div className="flex justify-end gap-8">
                  <span className="text-slate-500">Issue Date</span>
                  <span className="text-slate-900">
                    {formatLocalDate(invoice.issueDate)}
                  </span>
                </div>
              )}
              {invoice.dueDate && (
                <div className="mt-1 flex justify-end gap-8">
                  <span className="text-slate-500">Due Date</span>
                  <span className="text-slate-900">
                    {formatLocalDate(invoice.dueDate)}
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* Lines summary */}
          <div className="border-t border-slate-200 pt-4">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs font-medium uppercase text-slate-500">
                  <th className="pb-2">Description</th>
                  <th className="pb-2 text-right">Qty</th>
                  <th className="pb-2 text-right">Rate</th>
                  <th className="pb-2 text-right">Amount</th>
                </tr>
              </thead>
              <tbody>
                {invoice.lines.map((line) => (
                  <tr
                    key={line.id}
                    className="border-t border-slate-100 text-slate-700"
                  >
                    <td className="py-2">{line.description}</td>
                    <td className="py-2 text-right font-mono tabular-nums">
                      {line.quantity}
                    </td>
                    <td className="py-2 text-right font-mono tabular-nums">
                      {formatCurrency(line.unitPrice, invoice.currency)}
                    </td>
                    <td className="py-2 text-right font-mono tabular-nums">
                      {formatCurrency(line.amount, invoice.currency)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Totals */}
          <div className="flex justify-end border-t border-slate-200 pt-4">
            <div className="w-64 space-y-1 text-sm">
              <div className="flex justify-between">
                <span className="text-slate-500">Subtotal</span>
                <span className="font-mono tabular-nums">
                  {formatCurrency(invoice.subtotal, invoice.currency)}
                </span>
              </div>
              {invoice.taxAmount > 0 && (
                <div className="flex justify-between">
                  <span className="text-slate-500">
                    {invoice.taxLabel ?? "Tax"}
                  </span>
                  <span className="font-mono tabular-nums">
                    {formatCurrency(invoice.taxAmount, invoice.currency)}
                  </span>
                </div>
              )}
              <div className="flex justify-between border-t border-slate-200 pt-1 font-semibold text-slate-900">
                <span>Total</span>
                <span className="font-mono tabular-nums">
                  {formatCurrency(invoice.total, invoice.currency)}
                </span>
              </div>
            </div>
          </div>

          {/* Notes */}
          {invoice.notes && (
            <div className="border-t border-slate-200 pt-4">
              <p className="text-xs font-medium uppercase text-slate-500">
                Notes
              </p>
              <p className="mt-1 whitespace-pre-line text-sm text-slate-600">
                {invoice.notes}
              </p>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}
