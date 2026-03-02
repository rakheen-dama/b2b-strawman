"use client";

import { useState, useTransition } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  fetchUnbilledTime,
  createInvoiceDraft,
  validateInvoiceGeneration,
} from "@/app/(app)/org/[slug]/customers/[id]/invoice-actions";
import { formatCurrency, formatDuration, formatDate } from "@/lib/format";
import { ExpenseCategoryBadge } from "@/components/expenses/expense-category-badge";
import type {
  UnbilledTimeResponse,
  UnbilledProjectGroup,
  UnbilledTimeEntry,
  UnbilledExpenseEntry,
  ValidationCheck,
} from "@/lib/types";
import { Plus, ArrowLeft, CheckCircle2, AlertTriangle, Loader2 } from "lucide-react";
import { PrerequisiteModal } from "@/components/prerequisite/prerequisite-modal";
import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";
import type { PrerequisiteViolation } from "@/components/prerequisite/types";

/** Wraps formatCurrency in a try-catch to handle invalid currency codes gracefully. */
function safeFormatCurrency(amount: number, curr: string): string {
  try {
    return formatCurrency(amount, curr);
  } catch {
    return `${curr} ${amount.toFixed(2)}`;
  }
}

interface InvoiceGenerationDialogProps {
  customerId: string;
  customerName: string;
  slug: string;
  defaultCurrency: string;
}

export function InvoiceGenerationDialog({
  customerId,
  customerName,
  slug,
  defaultCurrency,
}: InvoiceGenerationDialogProps) {
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

  // Validation state
  const [validationChecks, setValidationChecks] = useState<ValidationCheck[] | null>(null);
  const [isValidating, setIsValidating] = useState(false);

  function resetState() {
    setStep(1);
    setError(null);
    setFromDate("");
    setToDate("");
    setCurrency(defaultCurrency);
    setUnbilledData(null);
    setSelectedEntryIds(new Set());
    setSelectedExpenseIds(new Set());
    setValidationChecks(null);
    setIsValidating(false);
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      resetState();
    }
    setOpen(newOpen);
  }

  async function handleNewInvoiceClick() {
    setCheckingPrereqs(true);
    try {
      const check = await checkPrerequisitesAction(
        "INVOICE_GENERATION",
        "CUSTOMER",
        customerId,
      );
      if (check.passed) {
        handleOpenChange(true);
      } else {
        setPrereqViolations(check.violations);
        setPrereqModalOpen(true);
      }
    } catch {
      // Fail-open: proceed to dialog if check throws
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
          toDate || undefined,
        );
        if (result.success && result.data) {
          setUnbilledData(result.data);
          // Auto-select entries matching the selected currency
          const matchingIds = new Set<string>();
          for (const project of result.data.projects) {
            for (const entry of project.entries) {
              if (entry.billingRateCurrency === currency) {
                matchingIds.add(entry.id);
              }
            }
          }
          setSelectedEntryIds(matchingIds);
          // Auto-select expenses matching the selected currency
          const matchingExpenseIds = new Set<string>();
          for (const expense of result.data.unbilledExpenses) {
            if (expense.currency === currency) {
              matchingExpenseIds.add(expense.id);
            }
          }
          setSelectedExpenseIds(matchingExpenseIds);
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
      if (next.has(entryId)) {
        next.delete(entryId);
      } else {
        next.add(entryId);
      }
      return next;
    });
  }

  function handleToggleProject(project: UnbilledProjectGroup) {
    const selectableEntries = project.entries.filter(
      (e) => e.billingRateCurrency === currency,
    );
    const allSelected = selectableEntries.every((e) => selectedEntryIds.has(e.id));

    setSelectedEntryIds((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        // Deselect all
        for (const entry of selectableEntries) {
          next.delete(entry.id);
        }
      } else {
        // Select all
        for (const entry of selectableEntries) {
          next.add(entry.id);
        }
      }
      return next;
    });
  }

  function handleToggleExpense(expenseId: string) {
    setSelectedExpenseIds((prev) => {
      const next = new Set(prev);
      if (next.has(expenseId)) {
        next.delete(expenseId);
      } else {
        next.add(expenseId);
      }
      return next;
    });
  }

  function handleToggleAllExpenses() {
    if (!unbilledData) return;
    const selectableExpenses = unbilledData.unbilledExpenses.filter(
      (e) => e.currency === currency,
    );
    const allSelected =
      selectableExpenses.length > 0 &&
      selectableExpenses.every((e) => selectedExpenseIds.has(e.id));

    setSelectedExpenseIds((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        for (const expense of selectableExpenses) {
          next.delete(expense.id);
        }
      } else {
        for (const expense of selectableExpenses) {
          next.add(expense.id);
        }
      }
      return next;
    });
  }

  function handleRunValidation() {
    if (selectedEntryIds.size === 0 && selectedExpenseIds.size === 0) return;
    setError(null);
    setIsValidating(true);

    startTransition(async () => {
      try {
        const result = await validateInvoiceGeneration(
          customerId,
          Array.from(selectedEntryIds),
        );
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
    if (selectedEntryIds.size === 0 && selectedExpenseIds.size === 0) return;
    setError(null);

    startTransition(async () => {
      try {
        const result = await createInvoiceDraft(slug, customerId, {
          customerId,
          currency,
          timeEntryIds: Array.from(selectedEntryIds),
          expenseIds:
            selectedExpenseIds.size > 0
              ? Array.from(selectedExpenseIds)
              : undefined,
        });
        if (result.success) {
          setOpen(false);
        } else {
          setError(result.error ?? "Failed to create invoice draft.");
        }
      } catch {
        setError("An unexpected error occurred.");
      }
    });
  }

  // Calculate running total of selected time entries + expenses
  const timeTotal = unbilledData
    ? unbilledData.projects.reduce((sum, project) => {
        return (
          sum +
          project.entries
            .filter((e) => selectedEntryIds.has(e.id))
            .reduce((s, e) => s + e.billableValue, 0)
        );
      }, 0)
    : 0;
  const expenseTotal = unbilledData
    ? unbilledData.unbilledExpenses
        .filter((e) => selectedExpenseIds.has(e.id))
        .reduce((s, e) => s + e.billableAmount, 0)
    : 0;
  const runningTotal = timeTotal + expenseTotal;

  const totalItemCount = selectedEntryIds.size + selectedExpenseIds.size;

  // Entries with no rate card configured (null/undefined rate — excludes $0.00 pro-bono rates)
  const nullRateEntries = unbilledData
    ? unbilledData.projects.flatMap((p) =>
        p.entries
          .filter((e) => e.billingRateSnapshot == null)
          .map((e) => ({ ...e, projectName: p.projectName })),
      )
    : [];

  return (
    <>
      <Button size="sm" onClick={handleNewInvoiceClick} disabled={checkingPrereqs}>
        {checkingPrereqs ? (
          <Loader2 className="mr-1.5 size-4 animate-spin" />
        ) : (
          <Plus className="mr-1.5 size-4" />
        )}
        New Invoice
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent className={step === 2 ? "sm:max-w-2xl" : undefined}>
        <DialogHeader>
          <DialogTitle>
            {step === 1 ? "Generate Invoice" : "Select Unbilled Items"}
          </DialogTitle>
          <DialogDescription>
            {step === 1
              ? `Create a new invoice for ${customerName} from unbilled time entries and expenses.`
              : `${totalItemCount} items selected for ${customerName}`}
          </DialogDescription>
        </DialogHeader>

        {step === 1 && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="invoice-from-date">From Date</Label>
                <Input
                  id="invoice-from-date"
                  type="date"
                  value={fromDate}
                  onChange={(e) => setFromDate(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="invoice-to-date">To Date</Label>
                <Input
                  id="invoice-to-date"
                  type="date"
                  value={toDate}
                  onChange={(e) => setToDate(e.target.value)}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="invoice-currency">Currency</Label>
              <Input
                id="invoice-currency"
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                placeholder="USD"
                maxLength={3}
                className="w-24"
              />
              <p className="text-xs text-slate-500">
                3-letter ISO currency code (e.g. USD, EUR, ZAR)
              </p>
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => setOpen(false)}
                disabled={isPending}
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handleFetchUnbilled}
                disabled={isPending || currency.length !== 3}
              >
                {isPending ? "Loading..." : "Fetch Unbilled Time"}
              </Button>
            </DialogFooter>
          </div>
        )}

        {step === 2 && unbilledData && (
          <div className="space-y-4">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-slate-600 dark:text-slate-400"
              onClick={() => setStep(1)}
            >
              <ArrowLeft className="mr-1 size-3.5" />
              Back
            </Button>

            {/* Null rate warning */}
            {nullRateEntries.length > 0 && (
              <div
                className="rounded-md border border-amber-200 bg-amber-50 p-3 dark:border-amber-800 dark:bg-amber-900/20"
                data-testid="null-rate-warning"
              >
                <p className="mb-1.5 text-sm font-medium text-amber-800 dark:text-amber-200">
                  {nullRateEntries.length} time{" "}
                  {nullRateEntries.length === 1
                    ? "entry has"
                    : "entries have"}{" "}
                  no rate card
                </p>
                <ul className="space-y-0.5">
                  {nullRateEntries.map((e) => (
                    <li
                      key={e.id}
                      className="text-xs text-amber-700 dark:text-amber-300"
                    >
                      {e.memberName}, {formatDuration(e.durationMinutes)} on{" "}
                      {formatDate(e.date)} ({e.projectName} / {e.taskTitle})
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {unbilledData.projects.length === 0 ? (
              <p className="py-8 text-center text-sm text-slate-500">
                No unbilled time entries found for this period.
              </p>
            ) : (
              <div className="max-h-96 space-y-4 overflow-y-auto">
                {unbilledData.projects.map((project) => (
                  <ProjectEntryGroup
                    key={project.projectId}
                    project={project}
                    currency={currency}
                    selectedEntryIds={selectedEntryIds}
                    onToggleEntry={handleToggleEntry}
                    onToggleProject={handleToggleProject}
                  />
                ))}
              </div>
            )}

            {/* Expenses section */}
            {unbilledData.unbilledExpenses.length > 0 && (
              <ExpenseSelectionSection
                expenses={unbilledData.unbilledExpenses}
                currency={currency}
                selectedExpenseIds={selectedExpenseIds}
                onToggleExpense={handleToggleExpense}
                onToggleAll={handleToggleAllExpenses}
              />
            )}

            {/* Running total */}
            <div className="flex items-center justify-between border-t border-slate-200 pt-3 dark:border-slate-800">
              <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Total ({totalItemCount} items)
              </span>
              <span className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                {safeFormatCurrency(runningTotal, currency)}
              </span>
            </div>

            {/* Validation Checklist */}
            {validationChecks && validationChecks.length > 0 && (
              <div
                data-testid="validation-checklist"
                className="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/50"
              >
                <p className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                  Pre-generation checks
                </p>
                <ul className="space-y-1">
                  {validationChecks.map((check, idx) => (
                    <li key={idx} className="flex items-center gap-2 text-sm">
                      {check.passed ? (
                        <CheckCircle2 className="size-4 shrink-0 text-green-600" />
                      ) : (
                        <AlertTriangle className="size-4 shrink-0 text-yellow-600" />
                      )}
                      <span
                        className={
                          check.passed
                            ? "text-slate-700 dark:text-slate-300"
                            : "text-yellow-800 dark:text-yellow-200"
                        }
                      >
                        {check.message}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {error && <p className="text-sm text-destructive">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => setOpen(false)}
                disabled={isPending}
              >
                Cancel
              </Button>
              {!validationChecks ? (
                <Button
                  type="button"
                  onClick={handleRunValidation}
                  disabled={isPending || isValidating || totalItemCount === 0}
                >
                  {isValidating ? "Validating..." : "Validate & Create Draft"}
                </Button>
              ) : (
                <Button
                  type="button"
                  onClick={handleCreateDraft}
                  disabled={isPending || totalItemCount === 0}
                >
                  {isPending ? "Creating..." : validationChecks.some((c) => !c.passed)
                    ? `Create Draft (${validationChecks.filter((c) => !c.passed).length} issues)`
                    : "Create Draft"}
                </Button>
              )}
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>

      {prereqModalOpen && (
        <PrerequisiteModal
          open={prereqModalOpen}
          onOpenChange={setPrereqModalOpen}
          context="INVOICE_GENERATION"
          violations={prereqViolations}
          entityType="CUSTOMER"
          entityId={customerId}
          slug={slug}
          onResolved={() => {
            setPrereqModalOpen(false);
            handleOpenChange(true);
          }}
        />
      )}
    </>
  );
}

// Sub-component for a project's entries group
function ProjectEntryGroup({
  project,
  currency,
  selectedEntryIds,
  onToggleEntry,
  onToggleProject,
}: {
  project: UnbilledProjectGroup;
  currency: string;
  selectedEntryIds: Set<string>;
  onToggleEntry: (id: string) => void;
  onToggleProject: (project: UnbilledProjectGroup) => void;
}) {
  const selectableEntries = project.entries.filter(
    (e) => e.billingRateCurrency === currency,
  );
  const allSelected =
    selectableEntries.length > 0 &&
    selectableEntries.every((e) => selectedEntryIds.has(e.id));

  return (
    <div className="rounded-lg border border-slate-200 dark:border-slate-800">
      <div className="flex items-center gap-3 border-b border-slate-200 px-4 py-2.5 dark:border-slate-800">
        <input
          type="checkbox"
          checked={allSelected}
          onChange={() => onToggleProject(project)}
          disabled={selectableEntries.length === 0}
          className="size-4 rounded accent-teal-600"
          aria-label={`Select all entries for ${project.projectName}`}
        />
        <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
          {project.projectName}
        </span>
        <span className="text-xs text-slate-500">
          {selectableEntries.length} of {project.entries.length} selectable
        </span>
      </div>
      <div className="divide-y divide-slate-100 dark:divide-slate-800/50">
        {project.entries.map((entry) => (
          <EntryRow
            key={entry.id}
            entry={entry}
            currency={currency}
            isSelected={selectedEntryIds.has(entry.id)}
            onToggle={onToggleEntry}
          />
        ))}
      </div>
    </div>
  );
}

// Sub-component for expense selection
function ExpenseSelectionSection({
  expenses,
  currency,
  selectedExpenseIds,
  onToggleExpense,
  onToggleAll,
}: {
  expenses: UnbilledExpenseEntry[];
  currency: string;
  selectedExpenseIds: Set<string>;
  onToggleExpense: (id: string) => void;
  onToggleAll: () => void;
}) {
  const selectableExpenses = expenses.filter((e) => e.currency === currency);
  const allSelected =
    selectableExpenses.length > 0 &&
    selectableExpenses.every((e) => selectedExpenseIds.has(e.id));

  // Group expenses by project
  const expensesByProject: Record<
    string,
    { projectName: string; expenses: UnbilledExpenseEntry[] }
  > = {};
  for (const expense of expenses) {
    if (!expensesByProject[expense.projectId]) {
      expensesByProject[expense.projectId] = {
        projectName: expense.projectName,
        expenses: [],
      };
    }
    expensesByProject[expense.projectId].expenses.push(expense);
  }

  return (
    <div data-testid="expense-selection-section">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
          Expenses
        </h3>
        <button
          type="button"
          onClick={onToggleAll}
          className="text-xs text-teal-600 hover:text-teal-700 dark:text-teal-400"
          disabled={selectableExpenses.length === 0}
        >
          {allSelected ? "Deselect All" : "Select All Expenses"}
        </button>
      </div>
      <div className="max-h-60 space-y-3 overflow-y-auto">
        {Object.entries(expensesByProject).map(
          ([projectId, { projectName, expenses: projectExpenses }]) => (
            <div
              key={projectId}
              className="rounded-lg border border-slate-200 dark:border-slate-800"
            >
              <div className="border-b border-slate-200 px-4 py-2 dark:border-slate-800">
                <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  {projectName}
                </span>
              </div>
              <div className="divide-y divide-slate-100 dark:divide-slate-800/50">
                {projectExpenses.map((expense) => {
                  const currencyMismatch = expense.currency !== currency;
                  return (
                    <label
                      key={expense.id}
                      className={`flex items-center gap-3 px-4 py-2 text-sm ${
                        currencyMismatch
                          ? "cursor-not-allowed opacity-50"
                          : "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={selectedExpenseIds.has(expense.id)}
                        onChange={() => onToggleExpense(expense.id)}
                        disabled={currencyMismatch}
                        className="size-4 rounded accent-teal-600"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-slate-900 dark:text-slate-100">
                            {expense.description}
                          </span>
                          <ExpenseCategoryBadge category={expense.category} />
                          {currencyMismatch && (
                            <span className="text-xs text-slate-400">
                              ({expense.currency})
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-slate-500">
                          {formatDate(expense.date)}
                          {expense.markupPercent != null && (
                            <> &middot; {expense.markupPercent}% markup</>
                          )}
                        </div>
                      </div>
                      <span className="shrink-0 text-right font-medium text-slate-700 dark:text-slate-300">
                        {safeFormatCurrency(
                          expense.billableAmount,
                          expense.currency,
                        )}
                      </span>
                    </label>
                  );
                })}
              </div>
            </div>
          ),
        )}
      </div>
    </div>
  );
}

function EntryRow({
  entry,
  currency,
  isSelected,
  onToggle,
}: {
  entry: UnbilledTimeEntry;
  currency: string;
  isSelected: boolean;
  onToggle: (id: string) => void;
}) {
  const currencyMismatch = entry.billingRateCurrency !== currency;

  return (
    <label
      className={`flex items-center gap-3 px-4 py-2 text-sm ${
        currencyMismatch
          ? "cursor-not-allowed opacity-50"
          : "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
      }`}
    >
      <input
        type="checkbox"
        checked={isSelected}
        onChange={() => onToggle(entry.id)}
        disabled={currencyMismatch}
        className="size-4 rounded accent-teal-600"
      />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-medium text-slate-900 dark:text-slate-100">
            {entry.taskTitle}
          </span>
          {currencyMismatch && (
            <span className="text-xs text-slate-400">
              ({entry.billingRateCurrency})
            </span>
          )}
        </div>
        <div className="text-xs text-slate-500">
          {entry.memberName} &middot; {formatDate(entry.date)} &middot;{" "}
          {formatDuration(entry.durationMinutes)}
        </div>
      </div>
      <span className="shrink-0 text-right font-medium text-slate-700 dark:text-slate-300">
        {formatCurrency(entry.billableValue, entry.billingRateCurrency)}
      </span>
    </label>
  );
}
