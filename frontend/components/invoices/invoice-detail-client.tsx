"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/invoices/status-badge";
import { InvoiceLineTable } from "@/components/invoices/invoice-line-table";
import { Eye, Copy, Check, RefreshCw } from "lucide-react";
import { formatCurrency, formatDate } from "@/lib/format";
import type {
  InvoiceResponse,
  InvoiceLineResponse,
  AddLineItemRequest,
  UpdateLineItemRequest,
  ValidationCheck,
  PaymentEvent,
} from "@/lib/types";
import {
  updateInvoice,
  deleteInvoice,
  approveInvoice,
  sendInvoice,
  recordPayment,
  voidInvoice,
  addLineItem,
  updateLineItem,
  deleteLineItem,
  refreshPaymentLink,
} from "@/app/(app)/org/[slug]/invoices/actions";
import { PaymentEventHistory } from "@/components/invoices/PaymentEventHistory";

interface InvoiceDetailClientProps {
  invoice: InvoiceResponse;
  slug: string;
  isAdmin: boolean;
  paymentEvents?: PaymentEvent[];
}

export function InvoiceDetailClient({
  invoice: initialInvoice,
  slug,
  isAdmin,
  paymentEvents,
}: InvoiceDetailClientProps) {
  const router = useRouter();
  const [invoice, setInvoice] = useState(initialInvoice);
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  // Draft edit fields
  const [dueDate, setDueDate] = useState(invoice.dueDate ?? "");
  const [notes, setNotes] = useState(invoice.notes ?? "");
  const [paymentTerms, setPaymentTerms] = useState(invoice.paymentTerms ?? "");
  const [taxAmount, setTaxAmount] = useState(String(invoice.taxAmount ?? 0));

  // Add line dialog state
  const [showAddLine, setShowAddLine] = useState(false);
  const [newLineDesc, setNewLineDesc] = useState("");
  const [newLineQty, setNewLineQty] = useState("1");
  const [newLineRate, setNewLineRate] = useState("0");

  // Edit line dialog state
  const [editingLine, setEditingLine] = useState<InvoiceLineResponse | null>(
    null,
  );
  const [editLineDesc, setEditLineDesc] = useState("");
  const [editLineQty, setEditLineQty] = useState("");
  const [editLineRate, setEditLineRate] = useState("");

  // Payment reference state
  const [paymentRef, setPaymentRef] = useState("");
  const [showPaymentForm, setShowPaymentForm] = useState(false);

  // Copy link state
  const [copied, setCopied] = useState(false);

  // Send validation override state
  const [showSendOverride, setShowSendOverride] = useState(false);
  const [sendValidationChecks, setSendValidationChecks] = useState<ValidationCheck[]>([]);

  const isDraft = invoice.status === "DRAFT";
  const isApproved = invoice.status === "APPROVED";
  const isSent = invoice.status === "SENT";
  const isPaid = invoice.status === "PAID";
  const isVoid = invoice.status === "VOID";

  function handleError(result: { success: boolean; error?: string }) {
    if (!result.success) {
      setError(result.error ?? "An error occurred.");
    }
  }

  async function handleCopyPaymentLink() {
    if (!invoice.paymentUrl) return;
    try {
      await navigator.clipboard.writeText(invoice.paymentUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError("Failed to copy link");
    }
  }

  function handleRegenerateLink() {
    setError(null);
    startTransition(async () => {
      const result = await refreshPaymentLink(
        slug,
        invoice.id,
        invoice.customerId,
      );
      if (result.success && result.invoice) {
        setInvoice(result.invoice);
      } else {
        handleError(result);
      }
    });
  }

  function handleSaveDraft() {
    setError(null);
    startTransition(async () => {
      const result = await updateInvoice(slug, invoice.id, invoice.customerId, {
        dueDate: dueDate || undefined,
        notes: notes || undefined,
        paymentTerms: paymentTerms || undefined,
        taxAmount: parseFloat(taxAmount) || 0,
      });
      if (result.success && result.invoice) {
        setInvoice(result.invoice);
      } else {
        handleError(result);
      }
    });
  }

  function handleDelete() {
    if (!confirm("Are you sure you want to delete this draft invoice?")) return;
    setError(null);
    startTransition(async () => {
      const result = await deleteInvoice(slug, invoice.id, invoice.customerId);
      if (result.success) {
        router.push(`/org/${slug}/invoices`);
      } else {
        handleError(result);
      }
    });
  }

  function handleApprove() {
    setError(null);
    startTransition(async () => {
      const result = await approveInvoice(
        slug,
        invoice.id,
        invoice.customerId,
      );
      if (result.success && result.invoice) {
        setInvoice(result.invoice);
      } else {
        handleError(result);
      }
    });
  }

  function handleSend() {
    setError(null);
    startTransition(async () => {
      const result = await sendInvoice(slug, invoice.id, invoice.customerId);
      if (result.success && result.invoice) {
        setInvoice(result.invoice);
      } else if (result.canOverride && result.validationChecks) {
        setSendValidationChecks(result.validationChecks);
        setShowSendOverride(true);
      } else {
        handleError(result);
      }
    });
  }

  function handleSendWithOverride() {
    setError(null);
    setShowSendOverride(false);
    startTransition(async () => {
      const result = await sendInvoice(
        slug,
        invoice.id,
        invoice.customerId,
        true,
      );
      if (result.success && result.invoice) {
        setInvoice(result.invoice);
        setSendValidationChecks([]);
      } else {
        handleError(result);
      }
    });
  }

  function handleRecordPayment() {
    setError(null);
    startTransition(async () => {
      const result = await recordPayment(
        slug,
        invoice.id,
        invoice.customerId,
        paymentRef ? { paymentReference: paymentRef } : undefined,
      );
      if (result.success && result.invoice) {
        setInvoice(result.invoice);
        setShowPaymentForm(false);
        setPaymentRef("");
      } else {
        handleError(result);
      }
    });
  }

  function handleVoid() {
    if (!confirm("Are you sure you want to void this invoice? This cannot be undone."))
      return;
    setError(null);
    startTransition(async () => {
      const result = await voidInvoice(slug, invoice.id, invoice.customerId);
      if (result.success && result.invoice) {
        setInvoice(result.invoice);
      } else {
        handleError(result);
      }
    });
  }

  function handlePreview() {
    window.open(`/api/invoices/${invoice.id}/preview`, "_blank");
  }

  function handleAddLine() {
    setShowAddLine(true);
    setNewLineDesc("");
    setNewLineQty("1");
    setNewLineRate("0");
  }

  function submitAddLine() {
    if (!newLineDesc.trim()) return;
    setError(null);
    startTransition(async () => {
      const request: AddLineItemRequest = {
        description: newLineDesc.trim(),
        quantity: parseFloat(newLineQty) || 1,
        unitPrice: parseFloat(newLineRate) || 0,
      };
      const result = await addLineItem(
        slug,
        invoice.id,
        invoice.customerId,
        request,
      );
      if (result.success && result.invoice) {
        setInvoice(result.invoice);
        setShowAddLine(false);
      } else {
        handleError(result);
      }
    });
  }

  function handleEditLine(line: InvoiceLineResponse) {
    setEditingLine(line);
    setEditLineDesc(line.description);
    setEditLineQty(String(line.quantity));
    setEditLineRate(String(line.unitPrice));
  }

  function submitEditLine() {
    if (!editingLine || !editLineDesc.trim()) return;
    setError(null);
    startTransition(async () => {
      const request: UpdateLineItemRequest = {
        description: editLineDesc.trim(),
        quantity: parseFloat(editLineQty) || 1,
        unitPrice: parseFloat(editLineRate) || 0,
      };
      const result = await updateLineItem(
        slug,
        invoice.id,
        editingLine!.id,
        invoice.customerId,
        request,
      );
      if (result.success && result.invoice) {
        setInvoice(result.invoice);
        setEditingLine(null);
      } else {
        handleError(result);
      }
    });
  }

  function handleDeleteLine(lineId: string) {
    setError(null);
    startTransition(async () => {
      const result = await deleteLineItem(
        slug,
        invoice.id,
        lineId,
        invoice.customerId,
      );
      if (result.success) {
        setInvoice((prev) => ({
          ...prev,
          lines: prev.lines.filter((l) => l.id !== lineId),
          subtotal: prev.lines
            .filter((l) => l.id !== lineId)
            .reduce((sum, l) => sum + l.amount, 0),
          total:
            prev.lines
              .filter((l) => l.id !== lineId)
              .reduce((sum, l) => sum + l.amount, 0) + prev.taxAmount,
        }));
      } else {
        handleError(result);
      }
    });
  }

  return (
    <div className="space-y-6">
      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300">
          {error}
        </div>
      )}

      {/* Send Validation Override Dialog */}
      {showSendOverride && sendValidationChecks.length > 0 && (
        <div
          data-testid="send-override-dialog"
          className="rounded-lg border border-yellow-200 bg-yellow-50 p-4 dark:border-yellow-900 dark:bg-yellow-950/50"
        >
          <h3 className="mb-2 font-medium text-yellow-800 dark:text-yellow-200">
            Validation issues found
          </h3>
          <p className="mb-3 text-sm text-yellow-700 dark:text-yellow-300">
            The following issues were found. As an admin/owner, you can override
            and send anyway.
          </p>
          <ul className="mb-4 space-y-1">
            {sendValidationChecks.map((check, idx) => (
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
            <Button
              variant="accent"
              size="sm"
              onClick={handleSendWithOverride}
              disabled={isPending}
            >
              Send Anyway
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowSendOverride(false)}
              disabled={isPending}
            >
              Cancel
            </Button>
          </div>
        </div>
      )}

      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
              {invoice.invoiceNumber ?? "Draft Invoice"}
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
              <Button variant="soft" size="sm" onClick={handlePreview}>
                <Eye className="mr-1.5 size-4" />
                Preview
              </Button>
              {isDraft && (
                <>
                  <Button
                    variant="accent"
                    size="sm"
                    onClick={handleApprove}
                    disabled={isPending}
                  >
                    Approve
                  </Button>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={handleDelete}
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
                    onClick={handleSend}
                    disabled={isPending}
                  >
                    Mark as Sent
                  </Button>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={handleVoid}
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
                    onClick={() => setShowPaymentForm(true)}
                    disabled={isPending}
                  >
                    Record Payment
                  </Button>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={handleVoid}
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

      {/* Payment Form (inline for SENT status) */}
      {showPaymentForm && isSent && (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
          <h3 className="mb-3 font-medium text-slate-900 dark:text-slate-100">
            Record Payment
          </h3>
          <div className="flex items-end gap-3">
            <div className="flex-1">
              <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
                Payment Reference (optional)
              </label>
              <input
                type="text"
                value={paymentRef}
                onChange={(e) => setPaymentRef(e.target.value)}
                placeholder="e.g. CHK-12345, Wire transfer"
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-600"
              />
            </div>
            <Button
              variant="accent"
              size="sm"
              onClick={handleRecordPayment}
              disabled={isPending}
            >
              Confirm Payment
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowPaymentForm(false)}
              disabled={isPending}
            >
              Cancel
            </Button>
          </div>
        </div>
      )}

      {/* Paid indicator */}
      {isPaid && (
        <div className="rounded-lg border border-green-200 bg-green-50 p-4 dark:border-green-800 dark:bg-green-950">
          <h3 className="font-medium text-green-800 dark:text-green-200">
            Payment Received
          </h3>
          <div className="mt-1 space-y-1 text-sm text-green-700 dark:text-green-300">
            {invoice.paidAt && <p>Paid on: {formatDate(invoice.paidAt)}</p>}
            {invoice.paymentReference && (
              <p>Reference: {invoice.paymentReference}</p>
            )}
          </div>
        </div>
      )}

      {/* Payment Link Section (SENT + paymentUrl non-null) */}
      {isSent && invoice.paymentUrl && (
        <div className="rounded-lg border border-teal-200 bg-teal-50 p-4 dark:border-teal-800 dark:bg-teal-950/50">
          <h3 className="mb-2 font-medium text-teal-800 dark:text-teal-200">
            Online Payment Link
          </h3>
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={invoice.paymentUrl}
              readOnly
              className="flex-1 rounded-md border border-teal-200 bg-white px-3 py-2 text-sm text-slate-700 dark:border-teal-800 dark:bg-slate-950 dark:text-slate-300"
            />
            <Button
              variant="outline"
              size="sm"
              onClick={handleCopyPaymentLink}
              className="shrink-0"
            >
              {copied ? (
                <Check className="size-4" />
              ) : (
                <Copy className="size-4" />
              )}
              <span className="ml-1.5">{copied ? "Copied" : "Copy Link"}</span>
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={handleRegenerateLink}
              disabled={isPending}
              className="shrink-0"
            >
              <RefreshCw
                className={`size-4 ${isPending ? "animate-spin" : ""}`}
              />
              <span className="ml-1.5">Regenerate</span>
            </Button>
          </div>
        </div>
      )}

      {/* Payment Event History */}
      {(isSent || isPaid) && (
        <PaymentEventHistory events={paymentEvents ?? []} />
      )}

      {/* Void indicator */}
      {isVoid && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950">
          <p className="font-medium text-red-700 dark:text-red-300">
            This invoice has been voided.
          </p>
        </div>
      )}

      {/* Draft Edit Form */}
      {isDraft && isAdmin && (
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
                onChange={(e) => setDueDate(e.target.value)}
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              />
            </div>
            <div>
              <label htmlFor="invoice-tax-amount" className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                Tax Amount
              </label>
              <input
                id="invoice-tax-amount"
                type="number"
                value={taxAmount}
                onChange={(e) => setTaxAmount(e.target.value)}
                min="0"
                step="0.01"
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              />
            </div>
            <div>
              <label htmlFor="invoice-payment-terms" className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                Payment Terms
              </label>
              <input
                id="invoice-payment-terms"
                type="text"
                value={paymentTerms}
                onChange={(e) => setPaymentTerms(e.target.value)}
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
                onChange={(e) => setNotes(e.target.value)}
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
              onClick={handleSaveDraft}
              disabled={isPending}
            >
              Save Changes
            </Button>
          </div>
        </div>
      )}

      {/* Read-only details for non-draft */}
      {!isDraft && (
        <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
          <h2 className="mb-4 font-semibold text-slate-900 dark:text-slate-100">
            Invoice Details
          </h2>
          <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
            {invoice.dueDate && (
              <div>
                <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">
                  Due Date
                </dt>
                <dd className="text-sm text-slate-900 dark:text-slate-100">
                  {formatDate(invoice.dueDate)}
                </dd>
              </div>
            )}
            {invoice.paymentTerms && (
              <div>
                <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">
                  Payment Terms
                </dt>
                <dd className="text-sm text-slate-900 dark:text-slate-100">
                  {invoice.paymentTerms}
                </dd>
              </div>
            )}
            {invoice.notes && (
              <div className="sm:col-span-2">
                <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">
                  Notes
                </dt>
                <dd className="text-sm text-slate-900 dark:text-slate-100">
                  {invoice.notes}
                </dd>
              </div>
            )}
          </dl>
        </div>
      )}

      {/* Line Items */}
      <InvoiceLineTable
        lines={invoice.lines}
        currency={invoice.currency}
        editable={isDraft && isAdmin}
        onAddLine={handleAddLine}
        onEditLine={handleEditLine}
        onDeleteLine={handleDeleteLine}
      />

      {/* Add Line Form */}
      {showAddLine && (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
          <h3 className="mb-3 font-medium text-slate-900 dark:text-slate-100">
            Add Line Item
          </h3>
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="sm:col-span-3">
              <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
                Description
              </label>
              <input
                type="text"
                value={newLineDesc}
                onChange={(e) => setNewLineDesc(e.target.value)}
                placeholder="Line item description"
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-600"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
                Quantity
              </label>
              <input
                type="number"
                value={newLineQty}
                onChange={(e) => setNewLineQty(e.target.value)}
                min="0.01"
                step="0.01"
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
                Unit Price
              </label>
              <input
                type="number"
                value={newLineRate}
                onChange={(e) => setNewLineRate(e.target.value)}
                min="0"
                step="0.01"
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              />
            </div>
            <div className="flex items-end gap-2">
              <Button
                variant="accent"
                size="sm"
                onClick={submitAddLine}
                disabled={isPending || !newLineDesc.trim()}
              >
                Add
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowAddLine(false)}
                disabled={isPending}
              >
                Cancel
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Line Form */}
      {editingLine && (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
          <h3 className="mb-3 font-medium text-slate-900 dark:text-slate-100">
            Edit Line Item
          </h3>
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="sm:col-span-3">
              <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
                Description
              </label>
              <input
                type="text"
                value={editLineDesc}
                onChange={(e) => setEditLineDesc(e.target.value)}
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
                Quantity
              </label>
              <input
                type="number"
                value={editLineQty}
                onChange={(e) => setEditLineQty(e.target.value)}
                min="0.01"
                step="0.01"
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
                Unit Price
              </label>
              <input
                type="number"
                value={editLineRate}
                onChange={(e) => setEditLineRate(e.target.value)}
                min="0"
                step="0.01"
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              />
            </div>
            <div className="flex items-end gap-2">
              <Button
                variant="accent"
                size="sm"
                onClick={submitEditLine}
                disabled={isPending || !editLineDesc.trim()}
              >
                Save
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setEditingLine(null)}
                disabled={isPending}
              >
                Cancel
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Totals */}
      <div className="flex justify-end">
        <div className="w-full max-w-xs space-y-2">
          <div className="flex justify-between text-sm text-slate-600 dark:text-slate-400">
            <span>Subtotal</span>
            <span>{formatCurrency(invoice.subtotal, invoice.currency)}</span>
          </div>
          <div className="flex justify-between text-sm text-slate-600 dark:text-slate-400">
            <span>Tax</span>
            <span>{formatCurrency(invoice.taxAmount, invoice.currency)}</span>
          </div>
          <div className="flex justify-between border-t border-slate-200 pt-2 font-semibold text-slate-900 dark:border-slate-800 dark:text-slate-100">
            <span>Total</span>
            <span>{formatCurrency(invoice.total, invoice.currency)}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
