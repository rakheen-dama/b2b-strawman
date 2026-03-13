"use client";

import { InvoiceLineTable } from "@/components/invoices/invoice-line-table";
import { useInvoiceDetail } from "@/components/invoices/use-invoice-detail";
import { InvoiceHeaderActions } from "@/components/invoices/invoice-header-actions";
import { InvoiceDraftForm } from "@/components/invoices/invoice-draft-form";
import { InvoiceDetailsReadonly } from "@/components/invoices/invoice-details-readonly";
import { AddLineForm, EditLineForm } from "@/components/invoices/invoice-line-editor";
import { InvoiceTotalsSection } from "@/components/invoices/invoice-totals-section";
import {
  SendValidationOverride,
  InvoicePaymentForm,
  PaidIndicator,
  PaymentLinkSection,
  VoidIndicator,
  PaymentHistorySection,
} from "@/components/invoices/invoice-status-sections";
import type {
  InvoiceResponse,
  PaymentEvent,
  TaxRateResponse,
} from "@/lib/types";

interface InvoiceDetailClientProps {
  invoice: InvoiceResponse;
  slug: string;
  isAdmin: boolean;
  paymentEvents?: PaymentEvent[];
  taxRates?: TaxRateResponse[];
}

export function InvoiceDetailClient({
  invoice: initialInvoice,
  slug,
  isAdmin,
  paymentEvents,
  taxRates = [],
}: InvoiceDetailClientProps) {
  const h = useInvoiceDetail({ initialInvoice, slug, taxRates });

  return (
    <div className="space-y-6">
      {h.error && (
        <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300">
          {h.error}
        </div>
      )}

      {/* Send Validation Override Dialog */}
      {h.showSendOverride && h.sendValidationChecks.length > 0 && (
        <SendValidationOverride
          validationChecks={h.sendValidationChecks}
          isPending={h.isPending}
          onSendWithOverride={h.handleSendWithOverride}
          onCancel={() => h.setShowSendOverride(false)}
        />
      )}

      {/* Header */}
      <InvoiceHeaderActions
        invoice={h.invoice}
        isAdmin={isAdmin}
        isPending={h.isPending}
        isDraft={h.isDraft}
        isApproved={h.isApproved}
        isSent={h.isSent}
        onPreview={h.handlePreview}
        onApprove={h.handleApprove}
        onDelete={h.handleDelete}
        onSend={h.handleSend}
        onVoid={h.handleVoid}
        onShowPaymentForm={() => h.setShowPaymentForm(true)}
      />

      {/* Payment Form (inline for SENT status) */}
      {h.showPaymentForm && h.isSent && (
        <InvoicePaymentForm
          paymentRef={h.paymentRef}
          onPaymentRefChange={h.setPaymentRef}
          isPending={h.isPending}
          onConfirm={h.handleRecordPayment}
          onCancel={() => h.setShowPaymentForm(false)}
        />
      )}

      {/* Paid indicator */}
      {h.isPaid && <PaidIndicator invoice={h.invoice} />}

      {/* Payment Link Section (SENT + paymentUrl non-null) */}
      {h.isSent && h.invoice.paymentUrl && (
        <PaymentLinkSection
          paymentUrl={h.invoice.paymentUrl}
          copied={h.copied}
          isPending={h.isPending}
          onCopy={h.handleCopyPaymentLink}
          onRegenerate={h.handleRegenerateLink}
        />
      )}

      {/* Payment Event History */}
      {(h.isSent || h.isPaid) && (
        <PaymentHistorySection paymentEvents={paymentEvents ?? []} />
      )}

      {/* Void indicator */}
      {h.isVoid && <VoidIndicator />}

      {/* Draft Edit Form */}
      {h.isDraft && isAdmin && (
        <InvoiceDraftForm
          dueDate={h.dueDate}
          onDueDateChange={h.setDueDate}
          notes={h.notes}
          onNotesChange={h.setNotes}
          paymentTerms={h.paymentTerms}
          onPaymentTermsChange={h.setPaymentTerms}
          taxAmount={h.taxAmount}
          onTaxAmountChange={h.setTaxAmount}
          hasPerLineTax={h.invoice.hasPerLineTax}
          isPending={h.isPending}
          onSave={h.handleSaveDraft}
        />
      )}

      {/* Read-only details for non-draft */}
      {!h.isDraft && <InvoiceDetailsReadonly invoice={h.invoice} />}

      {/* Line Items */}
      <InvoiceLineTable
        lines={h.invoice.lines}
        currency={h.invoice.currency}
        editable={h.isDraft && isAdmin}
        hasPerLineTax={h.invoice.hasPerLineTax}
        onAddLine={h.handleAddLine}
        onEditLine={h.handleEditLine}
        onDeleteLine={h.handleDeleteLine}
      />

      {/* Add Line Form */}
      {h.showAddLine && (
        <AddLineForm
          description={h.newLineDesc}
          onDescriptionChange={h.setNewLineDesc}
          quantity={h.newLineQty}
          onQuantityChange={h.setNewLineQty}
          unitPrice={h.newLineRate}
          onUnitPriceChange={h.setNewLineRate}
          taxRateId={h.newLineTaxRateId}
          onTaxRateIdChange={h.setNewLineTaxRateId}
          taxRates={taxRates}
          isPending={h.isPending}
          onSubmit={h.submitAddLine}
          onCancel={() => h.setShowAddLine(false)}
        />
      )}

      {/* Edit Line Form */}
      {h.editingLine && (
        <EditLineForm
          description={h.editLineDesc}
          onDescriptionChange={h.setEditLineDesc}
          quantity={h.editLineQty}
          onQuantityChange={h.setEditLineQty}
          unitPrice={h.editLineRate}
          onUnitPriceChange={h.setEditLineRate}
          taxRateId={h.editLineTaxRateId}
          onTaxRateIdChange={h.setEditLineTaxRateId}
          taxRates={taxRates}
          isPending={h.isPending}
          onSubmit={h.submitEditLine}
          onCancel={() => h.setEditingLine(null)}
        />
      )}

      {/* Totals */}
      <InvoiceTotalsSection invoice={h.invoice} />
    </div>
  );
}
