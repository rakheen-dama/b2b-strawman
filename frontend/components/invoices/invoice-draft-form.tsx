"use client";

import { Button } from "@/components/ui/button";

interface InvoiceDraftFormProps {
  dueDate: string;
  onDueDateChange: (value: string) => void;
  notes: string;
  onNotesChange: (value: string) => void;
  paymentTerms: string;
  onPaymentTermsChange: (value: string) => void;
  taxAmount: string;
  onTaxAmountChange: (value: string) => void;
  poNumber: string;
  onPoNumberChange: (value: string) => void;
  taxType: string;
  onTaxTypeChange: (value: string) => void;
  billingPeriodStart: string;
  onBillingPeriodStartChange: (value: string) => void;
  billingPeriodEnd: string;
  onBillingPeriodEndChange: (value: string) => void;
  hasPerLineTax: boolean;
  isPending: boolean;
  onSave: () => void;
}

const inputClass =
  "w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-600";

const labelClass =
  "mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300";

export function InvoiceDraftForm({
  dueDate,
  onDueDateChange,
  notes,
  onNotesChange,
  paymentTerms,
  onPaymentTermsChange,
  taxAmount,
  onTaxAmountChange,
  poNumber,
  onPoNumberChange,
  taxType,
  onTaxTypeChange,
  billingPeriodStart,
  onBillingPeriodStartChange,
  billingPeriodEnd,
  onBillingPeriodEndChange,
  hasPerLineTax,
  isPending,
  onSave,
}: InvoiceDraftFormProps) {
  return (
    <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
      <h2 className="mb-4 font-semibold text-slate-900 dark:text-slate-100">
        Invoice Details
      </h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="invoice-due-date" className={labelClass}>
            Due Date
          </label>
          <input
            id="invoice-due-date"
            type="date"
            value={dueDate}
            onChange={(e) => onDueDateChange(e.target.value)}
            className={inputClass}
          />
        </div>
        {!hasPerLineTax && (
          <div>
            <label htmlFor="invoice-tax-amount" className={labelClass}>
              Tax Amount
            </label>
            <input
              id="invoice-tax-amount"
              type="number"
              value={taxAmount}
              onChange={(e) => onTaxAmountChange(e.target.value)}
              min="0"
              step="0.01"
              className={inputClass}
            />
          </div>
        )}
        <div>
          <label htmlFor="invoice-payment-terms" className={labelClass}>
            Payment Terms
          </label>
          <input
            id="invoice-payment-terms"
            type="text"
            value={paymentTerms}
            onChange={(e) => onPaymentTermsChange(e.target.value)}
            placeholder="e.g. Net 30"
            maxLength={100}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="invoice-po-number" className={labelClass}>
            PO Number
          </label>
          <input
            id="invoice-po-number"
            type="text"
            value={poNumber}
            onChange={(e) => onPoNumberChange(e.target.value)}
            placeholder="e.g. PO-2026-001"
            maxLength={100}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="invoice-tax-type" className={labelClass}>
            Tax Type
          </label>
          <select
            id="invoice-tax-type"
            value={taxType}
            onChange={(e) => onTaxTypeChange(e.target.value)}
            className={inputClass}
          >
            <option value="">Select tax type…</option>
            <option value="VAT">VAT</option>
            <option value="GST">GST</option>
            <option value="SALES_TAX">Sales Tax</option>
            <option value="NONE">None</option>
          </select>
        </div>
        <div>
          <label htmlFor="invoice-billing-period-start" className={labelClass}>
            Billing Period Start
          </label>
          <input
            id="invoice-billing-period-start"
            type="date"
            value={billingPeriodStart}
            onChange={(e) => onBillingPeriodStartChange(e.target.value)}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="invoice-billing-period-end" className={labelClass}>
            Billing Period End
          </label>
          <input
            id="invoice-billing-period-end"
            type="date"
            value={billingPeriodEnd}
            onChange={(e) => onBillingPeriodEndChange(e.target.value)}
            className={inputClass}
          />
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="invoice-notes" className={labelClass}>
            Notes
          </label>
          <textarea
            id="invoice-notes"
            value={notes}
            onChange={(e) => onNotesChange(e.target.value)}
            rows={3}
            placeholder="Additional notes..."
            className={inputClass}
          />
        </div>
      </div>
      <div className="mt-4 flex justify-end">
        <Button
          variant="accent"
          size="sm"
          onClick={onSave}
          disabled={isPending}
        >
          Save Changes
        </Button>
      </div>
    </div>
  );
}
