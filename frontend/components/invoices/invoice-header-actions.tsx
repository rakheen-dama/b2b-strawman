"use client";

import { Button } from "@b2mash/ui/button";
import { DetailHero } from "@/components/shared/detail-hero";
import { StatusBadge } from "@/components/invoices/status-badge";
import { BroughtForwardBadge } from "@/components/invoices/brought-forward-badge";
import { Eye } from "lucide-react";
import { HelpTip } from "@/components/help-tip";
import { feeNoteHeroTiles } from "@/lib/fee-note-hero";
import { formatDate } from "@/lib/format";
import { RequiresCapability } from "@/lib/capabilities";
import type { InvoiceResponse } from "@/lib/types";

interface InvoiceHeaderActionsProps {
  invoice: InvoiceResponse;
  /** The business date (yyyy-MM-dd, SA calendar) from the server page — never computed here (ADR-396). */
  todayIso: string;
  isAdmin: boolean;
  isPending: boolean;
  isDraft: boolean;
  isApproved: boolean;
  isSent: boolean;
  /** SENT or PARTIALLY_PAID — a balance is due, so a payment can be recorded. */
  canRecordPayment: boolean;
  onPreview: () => void;
  onApprove: () => void;
  onDelete: () => void;
  onSend: () => void;
  onVoid: () => void;
  onShowPaymentForm: () => void;
}

export function InvoiceHeaderActions({
  invoice,
  todayIso,
  isAdmin,
  isPending,
  isDraft,
  isApproved,
  isSent,
  canRecordPayment,
  onPreview,
  onApprove,
  onDelete,
  onSend,
  onVoid,
  onShowPaymentForm,
}: InvoiceHeaderActionsProps) {
  return (
    <DetailHero
      title={
        <span className="inline-flex items-center gap-2">
          {invoice.invoiceNumber ?? "Draft fee note"}
          <HelpTip code="invoices.numbering" />
        </span>
      }
      status={
        <>
          <StatusBadge status={invoice.status} />
          <BroughtForwardBadge takeOnBatchId={invoice.takeOnBatchId} />
        </>
      }
      subtitle={
        <div className="flex flex-wrap gap-x-6 gap-y-1">
          <span>Client: {invoice.clientName}</span>
          {invoice.issueDate && <span>Issued: {formatDate(invoice.issueDate)}</span>}
          {invoice.dueDate && !isDraft && <span>Due: {formatDate(invoice.dueDate)}</span>}
          <span>Currency: {invoice.currency}</span>
        </div>
      }
      actions={
        // null for non-admins: DetailHero treats any fragment as content (empty actions div).
        isAdmin ? (
          <>
            <Button variant="soft" size="sm" onClick={onPreview}>
              <Eye className="mr-1.5 size-4" />
              Preview
            </Button>
            {isDraft && (
              <>
                <RequiresCapability cap="INVOICING">
                  <Button variant="accent" size="sm" onClick={onApprove} disabled={isPending}>
                    Approve
                  </Button>
                </RequiresCapability>
                <Button variant="destructive" size="sm" onClick={onDelete} disabled={isPending}>
                  Delete Draft
                </Button>
              </>
            )}
            {isApproved && (
              <>
                <Button variant="accent" size="sm" onClick={onSend} disabled={isPending}>
                  Send fee note
                </Button>
                <Button variant="destructive" size="sm" onClick={onVoid} disabled={isPending}>
                  Void
                </Button>
              </>
            )}
            {canRecordPayment && (
              <Button variant="accent" size="sm" onClick={onShowPaymentForm} disabled={isPending}>
                Record Payment
              </Button>
            )}
            {/* A part-paid fee note cannot be voided (backend 409) — SENT only. */}
            {isSent && (
              <Button variant="destructive" size="sm" onClick={onVoid} disabled={isPending}>
                Void
              </Button>
            )}
          </>
        ) : null
      }
      // Derived from the live fee note on every render (ADR-396): Record Payment, Approve, Send,
      // Void and line edits all setInvoice, so the strip never shows pre-action figures.
      tiles={feeNoteHeroTiles(invoice, todayIso)}
      tileTestIdPrefix="fee-note-hero-tile-"
    />
  );
}
