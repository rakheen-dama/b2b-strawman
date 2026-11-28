"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import type {
  InvoiceResponse,
  InvoiceLineResponse,
  AddLineItemRequest,
  UpdateLineItemRequest,
  UpdateInvoiceRequest,
  ValidationCheck,
  TaxRateResponse,
} from "@/lib/types";
import {
  fetchInvoice,
  updateInvoice,
  deleteInvoice,
  addLineItem,
  updateLineItem,
  deleteLineItem,
} from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";
import {
  approveInvoice,
  sendInvoice,
  recordPayment,
  voidInvoice,
  refreshPaymentLink,
} from "@/app/(app)/org/[slug]/invoices/invoice-payment-actions";
import type { RecordInvoicePaymentFormData } from "@/lib/schemas/invoice";

interface UseInvoiceDetailOptions {
  initialInvoice: InvoiceResponse;
  slug: string;
  taxRates: TaxRateResponse[];
}

export function useInvoiceDetail({ initialInvoice, slug, taxRates }: UseInvoiceDetailOptions) {
  const router = useRouter();
  const [invoice, setInvoice] = useState(initialInvoice);
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  // Draft edit fields
  const [dueDate, setDueDate] = useState(invoice.dueDate ?? "");
  const [notes, setNotes] = useState(invoice.notes ?? "");
  const [paymentTerms, setPaymentTerms] = useState(invoice.paymentTerms ?? "");
  const [taxAmount, setTaxAmount] = useState(String(invoice.taxAmount ?? 0));
  const [poNumber, setPoNumber] = useState(invoice.poNumber ?? "");
  const [taxType, setTaxType] = useState<NonNullable<UpdateInvoiceRequest["taxType"]> | "">(
    invoice.taxType ?? ""
  );
  const TAX_TYPES: ReadonlyArray<NonNullable<UpdateInvoiceRequest["taxType"]>> = [
    "VAT",
    "GST",
    "SALES_TAX",
    "NONE",
  ];
  const handleTaxTypeChange = (value: string) => {
    if (value === "") {
      setTaxType("");
    } else if ((TAX_TYPES as readonly string[]).includes(value)) {
      setTaxType(value as NonNullable<UpdateInvoiceRequest["taxType"]>);
    }
  };
  const [billingPeriodStart, setBillingPeriodStart] = useState(invoice.billingPeriodStart ?? "");
  const [billingPeriodEnd, setBillingPeriodEnd] = useState(invoice.billingPeriodEnd ?? "");

  // Default tax rate for new lines
  const defaultTaxRate = taxRates.find((r) => r.isDefault && r.active);

  // Add line dialog state
  const [showAddLine, setShowAddLine] = useState(false);
  const [newLineDesc, setNewLineDesc] = useState("");
  const [newLineQty, setNewLineQty] = useState("1");
  const [newLineRate, setNewLineRate] = useState("0");
  const [newLineTaxRateId, setNewLineTaxRateId] = useState(defaultTaxRate?.id ?? "none");

  // Edit line dialog state
  const [editingLine, setEditingLine] = useState<InvoiceLineResponse | null>(null);
  const [editLineDesc, setEditLineDesc] = useState("");
  const [editLineQty, setEditLineQty] = useState("");
  const [editLineRate, setEditLineRate] = useState("");
  const [editLineTaxRateId, setEditLineTaxRateId] = useState("none");

  // Record Payment form visibility
  const [showPaymentForm, setShowPaymentForm] = useState(false);

  // Copy link state
  const [copied, setCopied] = useState(false);

  // Send validation override state
  const [showSendOverride, setShowSendOverride] = useState(false);
  const [sendValidationChecks, setSendValidationChecks] = useState<ValidationCheck[]>([]);

  const isDraft = invoice.status === "DRAFT";
  const isApproved = invoice.status === "APPROVED";
  const isSent = invoice.status === "SENT";
  const isPartiallyPaid = invoice.status === "PARTIALLY_PAID";
  const isPaid = invoice.status === "PAID";
  const isVoid = invoice.status === "VOID";
  // ADR-347: a payment can be posted while a balance is due (SENT or PARTIALLY_PAID).
  const canRecordPayment = isSent || isPartiallyPaid;

  // The one way a fee note from the server reaches the page (add, edit and delete line, refresh,
  // Save Changes, status actions): lines, totals and the header Tax Amount field move together.
  // The header field is hidden while lines carry tax, so nobody can have typed into it: keep it at
  // the server's tax, or it would show (and Save Changes would re-send) the VAT of lines that have
  // since gone "None" or been deleted. On a header-tax note the typed value is kept. The field and
  // Save Changes are disabled while any of these requests is pending (isPending), so no value can
  // be typed between a request starting and its response landing here.
  function applyServerInvoice(next: InvoiceResponse) {
    if (invoice.hasPerLineTax) setTaxAmount(String(next.taxAmount ?? 0));
    setInvoice(next);
  }

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
      const result = await refreshPaymentLink(slug, invoice.id, invoice.clientId);
      if (result.success && result.invoice) {
        applyServerInvoice(result.invoice);
      } else {
        handleError(result);
      }
    });
  }

  function handleSaveDraft() {
    setError(null);
    startTransition(async () => {
      const result = await updateInvoice(slug, invoice.id, invoice.clientId, {
        dueDate: dueDate || undefined,
        notes: notes || undefined,
        paymentTerms: paymentTerms || undefined,
        taxAmount: invoice.hasPerLineTax ? undefined : parseFloat(taxAmount) || 0,
        poNumber: poNumber || undefined,
        taxType: taxType === "" ? undefined : taxType,
        billingPeriodStart: billingPeriodStart || undefined,
        billingPeriodEnd: billingPeriodEnd || undefined,
      });
      if (result.success && result.invoice) {
        applyServerInvoice(result.invoice);
      } else {
        handleError(result);
      }
    });
  }

  function handleDelete() {
    if (!confirm("Are you sure you want to delete this draft fee note?")) return;
    setError(null);
    startTransition(async () => {
      const result = await deleteInvoice(slug, invoice.id, invoice.clientId);
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
      const result = await approveInvoice(slug, invoice.id, invoice.clientId);
      if (result.success && result.invoice) {
        applyServerInvoice(result.invoice);
      } else {
        handleError(result);
      }
    });
  }

  function handleSend() {
    setError(null);
    startTransition(async () => {
      const result = await sendInvoice(slug, invoice.id, invoice.clientId);
      if (result.success && result.invoice) {
        applyServerInvoice(result.invoice);
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
      const result = await sendInvoice(slug, invoice.id, invoice.clientId, true);
      if (result.success && result.invoice) {
        applyServerInvoice(result.invoice);
        setSendValidationChecks([]);
      } else {
        handleError(result);
      }
    });
  }

  function handleRecordPayment(data: RecordInvoicePaymentFormData) {
    setError(null);
    startTransition(async () => {
      const result = await recordPayment(slug, invoice.id, invoice.clientId, {
        amount: data.amount,
        paymentMethod: data.paymentMethod,
        paymentReference: data.paymentReference || undefined,
      });
      if (result.success && result.invoice) {
        applyServerInvoice(result.invoice);
        setShowPaymentForm(false);
      } else {
        handleError(result);
      }
    });
  }

  function handleVoid() {
    if (!confirm("Are you sure you want to void this fee note? This cannot be undone.")) return;
    setError(null);
    startTransition(async () => {
      const result = await voidInvoice(slug, invoice.id, invoice.clientId);
      if (result.success && result.invoice) {
        applyServerInvoice(result.invoice);
      } else {
        handleError(result);
      }
    });
  }

  function handlePreview() {
    window.open(`/api/invoices/${invoice.id}/preview`, "_blank");
  }

  // Re-read the fee note after something outside this hook changed it (tariff and disbursement
  // dialogs). Runs as a transition so it counts as pending work like a line save.
  function handleRefresh() {
    setError(null);
    startTransition(async () => {
      await reloadInvoice();
    });
  }

  async function reloadInvoice() {
    const result = await fetchInvoice(invoice.id);
    if (result.success && result.invoice) {
      applyServerInvoice(result.invoice);
    } else {
      handleError(result);
    }
  }

  function handleAddLine() {
    setShowAddLine(true);
    setNewLineDesc("");
    setNewLineQty("1");
    setNewLineRate("0");
    setNewLineTaxRateId(defaultTaxRate?.id ?? "none");
  }

  function submitAddLine() {
    if (!newLineDesc.trim()) return;
    setError(null);
    startTransition(async () => {
      const request: AddLineItemRequest = {
        description: newLineDesc.trim(),
        quantity: parseFloat(newLineQty) || 1,
        unitPrice: parseFloat(newLineRate) || 0,
        taxRateId: newLineTaxRateId !== "none" ? newLineTaxRateId : null,
        // "None" picked in the tax select means no tax, not the org default. With no rates
        // loaded the select is hidden, nothing was chosen, and the backend default applies.
        ...(newLineTaxRateId === "none" && taxRates.length > 0 && { noTaxRate: true }),
      };
      const result = await addLineItem(slug, invoice.id, invoice.clientId, request);
      if (result.success && result.invoice) {
        applyServerInvoice(result.invoice);
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
    setEditLineTaxRateId(line.taxRateId ?? "none");
  }

  function submitEditLine() {
    if (!editingLine || !editLineDesc.trim()) return;
    setError(null);
    startTransition(async () => {
      const request: UpdateLineItemRequest = {
        description: editLineDesc.trim(),
        quantity: parseFloat(editLineQty) || 1,
        unitPrice: parseFloat(editLineRate) || 0,
        taxRateId: editLineTaxRateId !== "none" ? editLineTaxRateId : null,
        // Without the flag a null rate means "keep and re-price the line's current rate".
        ...(editLineTaxRateId === "none" && { clearTaxRate: true }),
      };
      const result = await updateLineItem(
        slug,
        invoice.id,
        editingLine!.id,
        invoice.clientId,
        request
      );
      if (result.success && result.invoice) {
        applyServerInvoice(result.invoice);
        setEditingLine(null);
      } else {
        handleError(result);
      }
    });
  }

  function handleDeleteLine(lineId: string) {
    setError(null);
    startTransition(async () => {
      const result = await deleteLineItem(slug, invoice.id, lineId, invoice.clientId);
      // The delete returns no body; the server recomputes subtotal, VAT and the tax mode, so
      // re-read the note rather than re-deriving totals here.
      if (result.success) {
        await reloadInvoice();
      } else {
        handleError(result);
      }
    });
  }

  return {
    invoice,
    isPending,
    error,
    setError,
    // Status flags
    isDraft,
    isApproved,
    isSent,
    isPartiallyPaid,
    isPaid,
    isVoid,
    canRecordPayment,
    // Draft fields
    dueDate,
    setDueDate,
    notes,
    setNotes,
    paymentTerms,
    setPaymentTerms,
    taxAmount,
    setTaxAmount,
    poNumber,
    setPoNumber,
    taxType,
    setTaxType: handleTaxTypeChange,
    billingPeriodStart,
    setBillingPeriodStart,
    billingPeriodEnd,
    setBillingPeriodEnd,
    // Add line state
    showAddLine,
    setShowAddLine,
    newLineDesc,
    setNewLineDesc,
    newLineQty,
    setNewLineQty,
    newLineRate,
    setNewLineRate,
    newLineTaxRateId,
    setNewLineTaxRateId,
    // Edit line state
    editingLine,
    setEditingLine,
    editLineDesc,
    setEditLineDesc,
    editLineQty,
    setEditLineQty,
    editLineRate,
    setEditLineRate,
    editLineTaxRateId,
    setEditLineTaxRateId,
    // Payment state
    showPaymentForm,
    setShowPaymentForm,
    // Copy state
    copied,
    // Send override state
    showSendOverride,
    setShowSendOverride,
    sendValidationChecks,
    // Handlers
    handleCopyPaymentLink,
    handleRegenerateLink,
    handleSaveDraft,
    handleDelete,
    handleApprove,
    handleSend,
    handleSendWithOverride,
    handleRecordPayment,
    handleVoid,
    handlePreview,
    handleAddLine,
    submitAddLine,
    handleEditLine,
    submitEditLine,
    handleDeleteLine,
    handleRefresh,
    // Tax rates
    taxRates,
  };
}

export type InvoiceDetailHook = ReturnType<typeof useInvoiceDetail>;
