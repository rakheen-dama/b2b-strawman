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
  hasPerLineTax: boolean;
  isPending: boolean;
  onSave: () => void;
}

export function InvoiceDraftForm({
  dueDate,
  onDueDateChange,
  notes,
  onNotesChange,
  paymentTerms,
  onPaymentTermsChange,
  taxAmount,
  onTaxAmountChange,
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
          <label htmlFor="invoice-due-date" className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
            Due Date
          </label>
          <input
            id="invoice-due-date"
            type="date"
            value={dueDate}
            onChange={(e) => onDueDateChange(e.target.value)}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          />
        </div>
        {!hasPerLineTax && (
          <div>
            <label htmlFor="invoice-tax-amount" className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
              Tax Amount
            </label>
            <input
              id="invoice-tax-amount"
              type="number"
              value={taxAmount}
              onChange={(e) => onTaxAmountChange(e.target.value)}
              min="0"
              step="0.01"
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
            />
          </div>
        )}
        <div>
          <label htmlFor="invoice-payment-terms" className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
            Payment Terms
          </label>
          <input
            id="invoice-payment-terms"
            type="text"
            value={paymentTerms}
            onChange={(e) => onPaymentTermsChange(e.target.value)}
            placeholder="e.g. Net 30"
            maxLength={100}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-600"
          />
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="invoice-notes" className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
            Notes
          </label>
          <textarea
            id="invoice-notes"
            value={notes}
            onChange={(e) => onNotesChange(e.target.value)}
            rows={3}
            placeholder="Additional notes..."
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-600"
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
