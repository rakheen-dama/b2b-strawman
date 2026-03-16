"use client";

import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/invoices/status-badge";
import { Eye } from "lucide-react";
import { HelpTip } from "@/components/help-tip";
import { formatDate } from "@/lib/format";
import { RequiresCapability } from "@/lib/capabilities";
import type { InvoiceResponse } from "@/lib/types";

interface InvoiceHeaderActionsProps {
  invoice: InvoiceResponse;
  isAdmin: boolean;
  isPending: boolean;
  isDraft: boolean;
  isApproved: boolean;
  isSent: boolean;
  onPreview: () => void;
  onApprove: () => void;
  onDelete: () => void;
  onSend: () => void;
  onVoid: () => void;
  onShowPaymentForm: () => void;
}

export function InvoiceHeaderActions({
  invoice,
  isAdmin,
  isPending,
  isDraft,
  isApproved,
  isSent,
  onPreview,
  onApprove,
  onDelete,
  onSend,
  onVoid,
  onShowPaymentForm,
}: InvoiceHeaderActionsProps) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <div className="flex items-center gap-3">
          <h1 className="flex items-center gap-2 font-display text-2xl text-slate-950 dark:text-slate-50">
            {invoice.invoiceNumber ?? "Draft Invoice"}
            <HelpTip code="invoices.numbering" />
          </h1>
          <StatusBadge status={invoice.status} />
        </div>
        <div className="mt-2 flex flex-wrap gap-x-6 gap-y-1 text-sm text-slate-600 dark:text-slate-400">
          <span>Customer: {invoice.customerName}</span>
          {invoice.issueDate && (
            <span>Issued: {formatDate(invoice.issueDate)}</span>
          )}
          {invoice.dueDate && !isDraft && (
            <span>Due: {formatDate(invoice.dueDate)}</span>
          )}
          <span>Currency: {invoice.currency}</span>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex shrink-0 flex-wrap gap-2">
        {isAdmin && (
          <>
            <Button variant="soft" size="sm" onClick={onPreview}>
              <Eye className="mr-1.5 size-4" />
              Preview
            </Button>
            {isDraft && (
              <>
                <RequiresCapability cap="INVOICING">
                  <Button
                    variant="accent"
                    size="sm"
                    onClick={onApprove}
                    disabled={isPending}
                  >
                    Approve
                  </Button>
                </RequiresCapability>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={onDelete}
                  disabled={isPending}
                >
                  Delete Draft
                </Button>
              </>
            )}
            {isApproved && (
              <>
                <Button
                  variant="accent"
                  size="sm"
                  onClick={onSend}
                  disabled={isPending}
                >
                  Send Invoice
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={onVoid}
                  disabled={isPending}
                >
                  Void
                </Button>
              </>
            )}
            {isSent && (
              <>
                <Button
                  variant="accent"
                  size="sm"
                  onClick={onShowPaymentForm}
                  disabled={isPending}
                >
                  Record Payment
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={onVoid}
                  disabled={isPending}
                >
                  Void
                </Button>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}
