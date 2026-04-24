"use client";

import { useState, useTransition } from "react";
import {
  fetchUnbilledTime,
  createInvoiceDraft,
  validateInvoiceGeneration,
} from "@/app/(app)/org/[slug]/customers/[id]/invoice-actions";
import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";
import type { UnbilledTimeResponse, UnbilledProjectGroup, ValidationCheck } from "@/lib/types";
import type { PrerequisiteViolation } from "@/components/prerequisite/types";

interface UseInvoiceGenerationOptions {
  customerId: string;
  slug: string;
  defaultCurrency: string;
}

export function useInvoiceGeneration({
  customerId,
  slug,
  defaultCurrency,
}: UseInvoiceGenerationOptions) {
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState<1 | 2>(1);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  // Prerequisite gate state
  const [checkingPrereqs, setCheckingPrereqs] = useState(false);
  const [prereqModalOpen, setPrereqModalOpen] = useState(false);
  const [prereqViolations, setPrereqViolations] = useState<PrerequisiteViolation[]>([]);

  // Step 1 state
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [currency, setCurrency] = useState(defaultCurrency);

  // Step 2 state
  const [unbilledData, setUnbilledData] = useState<UnbilledTimeResponse | null>(null);
  const [selectedEntryIds, setSelectedEntryIds] = useState<Set<string>>(new Set());
  const [selectedExpenseIds, setSelectedExpenseIds] = useState<Set<string>>(new Set());
  const [selectedDisbursementIds, setSelectedDisbursementIds] = useState<Set<string>>(new Set());

  // Validation state
  const [validationChecks, setValidationChecks] = useState<ValidationCheck[] | null>(null);
  const [isValidating, setIsValidating] = useState(false);
  // GAP-L-62: warnings returned by the backend on draft creation — today a
  // `tax_number_missing` code surfaces an inline banner on the customer detail
  // page post-close. Scoped to the hook so it resets on dialog reopen.
  const [draftWarnings, setDraftWarnings] = useState<string[]>([]);

  function resetState() {
    setStep(1);
    setError(null);
    setFromDate("");
    setToDate("");
    setCurrency(defaultCurrency);
    setUnbilledData(null);
    setSelectedEntryIds(new Set());
    setSelectedExpenseIds(new Set());
    setSelectedDisbursementIds(new Set());
    setValidationChecks(null);
    setIsValidating(false);
    setDraftWarnings([]);
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) resetState();
    setOpen(newOpen);
  }

  async function handleNewInvoiceClick() {
    setCheckingPrereqs(true);
    try {
      const check = await checkPrerequisitesAction("INVOICE_GENERATION", "CUSTOMER", customerId);
      if (check.passed) {
        handleOpenChange(true);
      } else {
        setPrereqViolations(check.violations);
        setPrereqModalOpen(true);
      }
    } catch {
      handleOpenChange(true);
    } finally {
      setCheckingPrereqs(false);
    }
  }

  function handleFetchUnbilled() {
    setError(null);
    startTransition(async () => {
      try {
        const result = await fetchUnbilledTime(
          customerId,
          fromDate || undefined,
          toDate || undefined
        );
        if (result.success && result.data) {
          setUnbilledData(result.data);
          const matchingIds = new Set<string>();
          for (const project of result.data.projects) {
            for (const entry of project.entries) {
              if (entry.billingRateCurrency === currency) matchingIds.add(entry.id);
            }
          }
          setSelectedEntryIds(matchingIds);
          const matchingExpenseIds = new Set<string>();
          for (const expense of result.data.unbilledExpenses) {
            if (expense.currency === currency) matchingExpenseIds.add(expense.id);
          }
          setSelectedExpenseIds(matchingExpenseIds);
          // Legal disbursements are ZAR-only in the MVP — only pre-select when the
          // invoice currency is ZAR. The backend returns `disbursements` only when
          // the `disbursements` module is enabled (legal vertical), so this is a
          // no-op for non-legal tenants.
          const matchingDisbursementIds = new Set<string>(
            currency === "ZAR" ? (result.data.disbursements ?? []).map((d) => d.id) : []
          );
          setSelectedDisbursementIds(matchingDisbursementIds);
          setStep(2);
        } else {
          setError(result.error ?? "Failed to fetch unbilled time.");
        }
      } catch {
        setError("An unexpected error occurred.");
      }
    });
  }

  function handleToggleEntry(entryId: string) {
    setSelectedEntryIds((prev) => {
      const next = new Set(prev);
      if (next.has(entryId)) next.delete(entryId);
      else next.add(entryId);
      return next;
    });
  }

  function handleToggleProject(project: UnbilledProjectGroup) {
    const selectableEntries = project.entries.filter((e) => e.billingRateCurrency === currency);
    const allSelected = selectableEntries.every((e) => selectedEntryIds.has(e.id));

    setSelectedEntryIds((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        for (const entry of selectableEntries) next.delete(entry.id);
      } else {
        for (const entry of selectableEntries) next.add(entry.id);
      }
      return next;
    });
  }

  function handleToggleExpense(expenseId: string) {
    setSelectedExpenseIds((prev) => {
      const next = new Set(prev);
      if (next.has(expenseId)) next.delete(expenseId);
      else next.add(expenseId);
      return next;
    });
  }

  function handleToggleAllExpenses() {
    if (!unbilledData) return;
    const selectableExpenses = unbilledData.unbilledExpenses.filter((e) => e.currency === currency);
    const allSelected =
      selectableExpenses.length > 0 &&
      selectableExpenses.every((e) => selectedExpenseIds.has(e.id));

    setSelectedExpenseIds((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        for (const expense of selectableExpenses) next.delete(expense.id);
      } else {
        for (const expense of selectableExpenses) next.add(expense.id);
      }
      return next;
    });
  }

  function handleToggleDisbursement(disbursementId: string) {
    setSelectedDisbursementIds((prev) => {
      const next = new Set(prev);
      if (next.has(disbursementId)) next.delete(disbursementId);
      else next.add(disbursementId);
      return next;
    });
  }

  function handleToggleAllDisbursements() {
    if (!unbilledData) return;
    const disbursements = unbilledData.disbursements ?? [];
    // Legal disbursements are ZAR-only in the MVP; if the invoice currency is
    // anything else, there are no selectable rows.
    const selectableDisbursements = currency === "ZAR" ? disbursements : [];
    const allSelected =
      selectableDisbursements.length > 0 &&
      selectableDisbursements.every((d) => selectedDisbursementIds.has(d.id));

    setSelectedDisbursementIds((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        for (const d of selectableDisbursements) next.delete(d.id);
      } else {
        for (const d of selectableDisbursements) next.add(d.id);
      }
      return next;
    });
  }

  function handleRunValidation() {
    if (
      selectedEntryIds.size === 0 &&
      selectedExpenseIds.size === 0 &&
      selectedDisbursementIds.size === 0
    )
      return;
    setError(null);
    setIsValidating(true);

    startTransition(async () => {
      try {
        const result = await validateInvoiceGeneration(customerId, Array.from(selectedEntryIds));
        if (result.success && result.checks) {
          setValidationChecks(result.checks);
        } else {
          setError(result.error ?? "Failed to validate.");
        }
      } catch {
        setError("Failed to run validation.");
      } finally {
        setIsValidating(false);
      }
    });
  }

  function handleCreateDraft() {
    if (
      selectedEntryIds.size === 0 &&
      selectedExpenseIds.size === 0 &&
      selectedDisbursementIds.size === 0
    )
      return;
    setError(null);

    startTransition(async () => {
      try {
        const result = await createInvoiceDraft(slug, customerId, {
          customerId,
          currency,
          timeEntryIds: Array.from(selectedEntryIds),
          expenseIds: selectedExpenseIds.size > 0 ? Array.from(selectedExpenseIds) : undefined,
          disbursementIds:
            selectedDisbursementIds.size > 0 ? Array.from(selectedDisbursementIds) : undefined,
        });
        if (result.success) {
          // Expose any backend-emitted warnings (e.g. `tax_number_missing`) so the
          // caller can render a persistent post-create banner. See GAP-L-62.
          setDraftWarnings(result.invoice?.warnings ?? []);
          setOpen(false);
        } else {
          setError(result.error ?? "Failed to create invoice draft.");
        }
      } catch {
        setError("An unexpected error occurred.");
      }
    });
  }

  // Computed values
  const timeTotal = unbilledData
    ? unbilledData.projects.reduce(
        (sum, project) =>
          sum +
          project.entries
            .filter((e) => selectedEntryIds.has(e.id))
            .reduce((s, e) => s + (e.billableValue ?? 0), 0),
        0
      )
    : 0;

  const expenseTotal = unbilledData
    ? unbilledData.unbilledExpenses
        .filter((e) => selectedExpenseIds.has(e.id))
        .reduce((s, e) => s + e.billableAmount, 0)
    : 0;

  const disbursementTotal = unbilledData
    ? (unbilledData.disbursements ?? [])
        .filter((d) => selectedDisbursementIds.has(d.id))
        .reduce((s, d) => s + d.amount + d.vatAmount, 0)
    : 0;

  const runningTotal = timeTotal + expenseTotal + disbursementTotal;
  const totalItemCount =
    selectedEntryIds.size + selectedExpenseIds.size + selectedDisbursementIds.size;

  const nullRateEntries = unbilledData
    ? unbilledData.projects.flatMap((p) =>
        p.entries
          .filter((e) => e.billingRateSnapshot == null && e.rateSource !== "RESOLVED")
          .map((e) => ({ ...e, projectName: p.projectName }))
      )
    : [];

  return {
    open,
    step,
    setStep,
    error,
    isPending,
    // Prereq state
    checkingPrereqs,
    prereqModalOpen,
    setPrereqModalOpen,
    prereqViolations,
    // Step 1
    fromDate,
    setFromDate,
    toDate,
    setToDate,
    currency,
    setCurrency,
    // Step 2
    unbilledData,
    selectedEntryIds,
    selectedExpenseIds,
    selectedDisbursementIds,
    validationChecks,
    isValidating,
    // Computed
    runningTotal,
    totalItemCount,
    nullRateEntries,
    draftWarnings,
    // Handlers
    handleOpenChange,
    handleNewInvoiceClick,
    handleFetchUnbilled,
    handleToggleEntry,
    handleToggleProject,
    handleToggleExpense,
    handleToggleAllExpenses,
    handleToggleDisbursement,
    handleToggleAllDisbursements,
    handleRunValidation,
    handleCreateDraft,
  };
}
