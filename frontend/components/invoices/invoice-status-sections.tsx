"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Copy, Check, RefreshCw } from "lucide-react";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { formatCurrency, formatDate } from "@/lib/format";
import { PaymentEventHistory } from "@/components/invoices/PaymentEventHistory";
import {
  recordInvoicePaymentSchema,
  type RecordInvoicePaymentFormData,
} from "@/lib/schemas/invoice";
import type {
  InvoiceResponse,
  ManualPaymentMethod,
  ValidationCheck,
  PaymentEvent,
} from "@/lib/types";

// --- Send Validation Override ---

interface SendOverrideProps {
  validationChecks: ValidationCheck[];
  isPending: boolean;
  onSendWithOverride: () => void;
  onCancel: () => void;
}

export function SendValidationOverride({
  validationChecks,
  isPending,
  onSendWithOverride,
  onCancel,
}: SendOverrideProps) {
  return (
    <div
      data-testid="send-override-dialog"
      className="rounded-lg border border-yellow-200 bg-yellow-50 p-4 dark:border-yellow-900 dark:bg-yellow-950/50"
    >
      <h3 className="mb-2 font-medium text-yellow-800 dark:text-yellow-200">
        Validation issues found
      </h3>
      <p className="mb-3 text-sm text-yellow-700 dark:text-yellow-300">
        The following issues were found. As an admin/owner, you can override and send anyway.
      </p>
      <ul className="mb-4 space-y-1">
        {validationChecks.map((check, idx) => (
          <li key={idx} className="flex items-center gap-2 text-sm">
            <span
              className={
                check.passed
                  ? "text-green-700 dark:text-green-300"
                  : "text-yellow-800 dark:text-yellow-200"
              }
            >
              {check.passed ? "\u2713" : "\u2717"} {check.message}
            </span>
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <Button variant="accent" size="sm" onClick={onSendWithOverride} disabled={isPending}>
          Send Anyway
        </Button>
        <Button variant="ghost" size="sm" onClick={onCancel} disabled={isPending}>
          Cancel
        </Button>
      </div>
    </div>
  );
}

// --- Payment Form ---

const MANUAL_METHOD_OPTIONS: ReadonlyArray<{ value: ManualPaymentMethod; label: string }> = [
  { value: "EFT", label: "EFT" },
  { value: "CARD", label: "Card" },
  { value: "CASH", label: "Cash" },
  { value: "OTHER", label: "Other" },
];

interface PaymentFormProps {
  invoice: InvoiceResponse;
  isPending: boolean;
  onConfirm: (data: RecordInvoicePaymentFormData) => void;
  onCancel: () => void;
}

/**
 * Record Payment (Phase 88 U15, ADR-347): the money that moved, how it arrived and an optional
 * reference. Amount defaults to the balance due; a part payment leaves the fee note
 * PARTIALLY_PAID. Over-balance is the backend's rule (409, surfaced by the page error banner).
 */
export function InvoicePaymentForm({ invoice, isPending, onConfirm, onCancel }: PaymentFormProps) {
  const form = useForm<RecordInvoicePaymentFormData>({
    resolver: zodResolver(recordInvoicePaymentSchema),
    defaultValues: {
      amount: invoice.balanceDue ?? invoice.total,
      paymentMethod: "EFT",
      paymentReference: "",
    },
  });

  return (
    <div
      data-testid="record-payment-form"
      className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50"
    >
      <h3 className="font-medium text-slate-900 dark:text-slate-100">Record Payment</h3>
      <p
        data-testid="payment-form-balance-due"
        className="mt-1 mb-3 text-sm text-slate-600 dark:text-slate-400"
      >
        Balance due {formatCurrency(invoice.balanceDue ?? invoice.total, invoice.currency)}
      </p>
      <Form {...form}>
        <form noValidate onSubmit={form.handleSubmit(onConfirm)} className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-3">
            <FormField
              control={form.control}
              name="amount"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Amount</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0.01"
                      placeholder="0.00"
                      {...field}
                      onChange={(e) => field.onChange(parseFloat(e.target.value) || 0)}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="paymentMethod"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Payment method</FormLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <FormControl>
                      <SelectTrigger data-testid="payment-method-select" className="w-full">
                        <SelectValue placeholder="Select method" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {MANUAL_METHOD_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="paymentReference"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Payment Reference (optional)</FormLabel>
                  <FormControl>
                    <Input placeholder="e.g. CHK-12345, Wire transfer" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <div className="flex gap-2">
            <Button type="submit" variant="accent" size="sm" disabled={isPending}>
              Confirm Payment
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={onCancel} disabled={isPending}>
              Cancel
            </Button>
          </div>
        </form>
      </Form>
    </div>
  );
}

// --- Paid Indicator ---

export function PaidIndicator({ invoice }: { invoice: InvoiceResponse }) {
  return (
    <div className="rounded-lg border border-green-200 bg-green-50 p-4 dark:border-green-800 dark:bg-green-950">
      <h3 className="font-medium text-green-800 dark:text-green-200">Payment Received</h3>
      <div className="mt-1 space-y-1 text-sm text-green-700 dark:text-green-300">
        {invoice.paidAt && <p>Paid on: {formatDate(invoice.paidAt)}</p>}
        {invoice.paymentReference && <p>Reference: {invoice.paymentReference}</p>}
      </div>
    </div>
  );
}

// --- Partially Paid Indicator ---

export function PartiallyPaidIndicator({ invoice }: { invoice: InvoiceResponse }) {
  return (
    <div
      data-testid="partially-paid-indicator"
      className="rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950"
    >
      <h3 className="font-medium text-amber-800 dark:text-amber-200">Part payment received</h3>
      <p className="mt-1 text-sm text-amber-700 dark:text-amber-300">
        Paid {formatCurrency(invoice.amountPaid ?? 0, invoice.currency)} · Balance due{" "}
        <span data-testid="invoice-balance-due" className="font-medium">
          {formatCurrency(invoice.balanceDue ?? invoice.total, invoice.currency)}
        </span>
      </p>
    </div>
  );
}

// --- Payment Link Section ---

interface PaymentLinkSectionProps {
  paymentUrl: string;
  copied: boolean;
  isPending: boolean;
  onCopy: () => void;
  onRegenerate: () => void;
}

export function PaymentLinkSection({
  paymentUrl,
  copied,
  isPending,
  onCopy,
  onRegenerate,
}: PaymentLinkSectionProps) {
  return (
    <div className="rounded-lg border border-teal-200 bg-teal-50 p-4 dark:border-teal-800 dark:bg-teal-950/50">
      <h3 className="mb-2 font-medium text-teal-800 dark:text-teal-200">Online Payment Link</h3>
      <div className="flex items-center gap-2">
        <input
          type="text"
          value={paymentUrl}
          readOnly
          className="flex-1 rounded-md border border-teal-200 bg-white px-3 py-2 text-sm text-slate-700 dark:border-teal-800 dark:bg-slate-950 dark:text-slate-300"
        />
        <Button variant="outline" size="sm" onClick={onCopy} className="shrink-0">
          {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
          <span className="ml-1.5">{copied ? "Copied" : "Copy Link"}</span>
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={onRegenerate}
          disabled={isPending}
          className="shrink-0"
        >
          <RefreshCw className={`size-4 ${isPending ? "animate-spin" : ""}`} />
          <span className="ml-1.5">Regenerate</span>
        </Button>
      </div>
    </div>
  );
}

// --- Void Indicator ---

export function VoidIndicator() {
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950">
      <p className="font-medium text-red-700 dark:text-red-300">This invoice has been voided.</p>
    </div>
  );
}

// --- Payment Event History (re-export wrapper) ---

interface PaymentHistorySectionProps {
  paymentEvents: PaymentEvent[];
}

export function PaymentHistorySection({ paymentEvents }: PaymentHistorySectionProps) {
  return <PaymentEventHistory events={paymentEvents} />;
}
