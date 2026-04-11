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

interface UseInvoiceDetailOptions {
  initialInvoice: InvoiceResponse;
  slug: string;
  taxRates: TaxRateResponse[];
}

export function useInvoiceDetail({
  initialInvoice,
  slug,
  taxRates,
}: UseInvoiceDetailOptions) {
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
  const [taxType, setTaxType] = useState<
    NonNullable<UpdateInvoiceRequest["taxType"]> | ""
  >(invoice.taxType ?? "");
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
  const [billingPeriodStart, setBillingPeriodStart] = useState(
    invoice.billingPeriodStart ?? "",
  );
  const [billingPeriodEnd, setBillingPeriodEnd] = useState(
    invoice.billingPeriodEnd ?? "",
  );

  // Default tax rate for new lines
  const defaultTaxRate = taxRates.find((r) => r.isDefault && r.active);

  // Add line dialog state
  const [showAddLine, setShowAddLine] = useState(false);
  const [newLineDesc, setNewLineDesc] = useState("");
  const [newLineQty, setNewLineQty] = useState("1");
  const [newLineRate, setNewLineRate] = useState("0");
  const [newLineTaxRateId, setNewLineTaxRateId] = useState(
    defaultTaxRate?.id ?? "none",
  );

  // Edit line dialog state
  const [editingLine, setEditingLine] = useState<InvoiceLineResponse | null>(
    null,
  );
  const [editLineDesc, setEditLineDesc] = useState("");
  const [editLineQty, setEditLineQty] = useState("");
  const [editLineRate, setEditLineRate] = useState("");
  const [editLineTaxRateId, setEditLineTaxRateId] = useState("none");

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
        taxAmount: invoice.hasPerLineTax ? undefined : (parseFloat(taxAmount) || 0),
        poNumber: poNumber || undefined,
        taxType: taxType === "" ? undefined : taxType,
        billingPeriodStart: billingPeriodStart || undefined,
        billingPeriodEnd: billingPeriodEnd || undefined,
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
      const result = await approveInvoice(slug, invoice.id, invoice.customerId);
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
      const result = await sendInvoice(slug, invoice.id, invoice.customerId, true);
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

  async function handleRefresh() {
    setError(null);
    const result = await fetchInvoice(invoice.id);
    if (result.success && result.invoice) {
      setInvoice(result.invoice);
    } else if (result.error) {
      setError(result.error);
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
      };
      const result = await addLineItem(slug, invoice.id, invoice.customerId, request);
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
      const result = await deleteLineItem(slug, invoice.id, lineId, invoice.customerId);
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

  return {
    invoice,
    isPending,
    error,
    setError,
    // Status flags
    isDraft,
    isApproved,
    isSent,
    isPaid,
    isVoid,
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
    paymentRef,
    setPaymentRef,
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
