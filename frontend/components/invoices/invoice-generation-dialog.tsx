"use client";

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
import { formatCurrency, formatDuration, formatDate } from "@/lib/format";
import { ExpenseCategoryBadge } from "@/components/expenses/expense-category-badge";
import type { UnbilledProjectGroup, UnbilledTimeEntry, UnbilledExpenseEntry } from "@/lib/types";
import { Plus, ArrowLeft, CheckCircle2, AlertTriangle, Loader2 } from "lucide-react";
import { HelpTip } from "@/components/help-tip";
import { PrerequisiteModal } from "@/components/prerequisite/prerequisite-modal";
import { useInvoiceGeneration } from "@/components/invoices/use-invoice-generation";
import { TerminologyText } from "@/components/terminology-text";
import { cn } from "@/lib/utils";

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
  const h = useInvoiceGeneration({ customerId, slug, defaultCurrency });

  return (
    <>
      <Button size="sm" onClick={h.handleNewInvoiceClick} disabled={h.checkingPrereqs}>
        {h.checkingPrereqs ? (
          <Loader2 className="mr-1.5 size-4 animate-spin" />
        ) : (
          <Plus className="mr-1.5 size-4" />
        )}
        <TerminologyText template="New {Invoice}" />
      </Button>

      <Dialog open={h.open} onOpenChange={h.handleOpenChange}>
        <DialogContent className={h.step === 2 ? "sm:max-w-2xl" : undefined}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              {h.step === 1 ? (
                <TerminologyText template="Generate {Invoice}" />
              ) : (
                "Select Unbilled Items"
              )}
              {h.step === 1 && <HelpTip code="invoices.unbilledTime" />}
            </DialogTitle>
            <DialogDescription>
              {h.step === 1 ? (
                <TerminologyText
                  template={`Create a new {invoice} for ${customerName} from unbilled time entries and expenses.`}
                />
              ) : (
                `${h.totalItemCount} items selected for ${customerName}`
              )}
            </DialogDescription>
          </DialogHeader>

          {h.step === 1 && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="invoice-from-date">From Date</Label>
                  <Input
                    id="invoice-from-date"
                    type="date"
                    value={h.fromDate}
                    onChange={(e) => h.setFromDate(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="invoice-to-date">To Date</Label>
                  <Input
                    id="invoice-to-date"
                    type="date"
                    value={h.toDate}
                    onChange={(e) => h.setToDate(e.target.value)}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="invoice-currency">Currency</Label>
                <Input
                  id="invoice-currency"
                  value={h.currency}
                  onChange={(e) => h.setCurrency(e.target.value.toUpperCase())}
                  placeholder="USD"
                  maxLength={3}
                  className="w-24"
                />
                <p className="text-xs text-slate-500">
                  3-letter ISO currency code (e.g. USD, EUR, ZAR)
                </p>
              </div>
              {h.error && <p className="text-destructive text-sm">{h.error}</p>}
              <DialogFooter>
                <Button
                  type="button"
                  variant="plain"
                  onClick={() => h.handleOpenChange(false)}
                  disabled={h.isPending}
                >
                  Cancel
                </Button>
                <Button
                  type="button"
                  onClick={h.handleFetchUnbilled}
                  disabled={h.isPending || h.currency.length !== 3}
                >
                  {h.isPending ? "Loading..." : "Fetch Unbilled Time"}
                </Button>
              </DialogFooter>
            </div>
          )}

          {h.step === 2 && h.unbilledData && (
            <div className="space-y-4">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-slate-600 dark:text-slate-400"
                onClick={() => h.setStep(1)}
              >
                <ArrowLeft className="mr-1 size-3.5" />
                Back
              </Button>

              {/* Null rate warning */}
              {h.nullRateEntries.length > 0 && (
                <div
                  className="rounded-md border border-amber-200 bg-amber-50 p-3 dark:border-amber-800 dark:bg-amber-900/20"
                  data-testid="null-rate-warning"
                >
                  <p className="mb-1.5 text-sm font-medium text-amber-800 dark:text-amber-200">
                    {h.nullRateEntries.length} time{" "}
                    {h.nullRateEntries.length === 1 ? "entry has" : "entries have"} no rate card
                  </p>
                  <ul className="space-y-0.5">
                    {h.nullRateEntries.map((e) => (
                      <li key={e.id} className="text-xs text-amber-700 dark:text-amber-300">
                        {e.memberName}, {formatDuration(e.durationMinutes)} on {formatDate(e.date)}{" "}
                        ({e.projectName} / {e.taskTitle})
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {h.unbilledData.projects.length === 0 ? (
                <p className="py-8 text-center text-sm text-slate-500">
                  No unbilled time entries found for this period.
                </p>
              ) : (
                <div className="max-h-96 space-y-4 overflow-y-auto">
                  {h.unbilledData.projects.map((project) => (
                    <ProjectEntryGroup
                      key={project.projectId}
                      project={project}
                      currency={h.currency}
                      selectedEntryIds={h.selectedEntryIds}
                      onToggleEntry={h.handleToggleEntry}
                      onToggleProject={h.handleToggleProject}
                    />
                  ))}
                </div>
              )}

              {/* Expenses section */}
              {h.unbilledData.unbilledExpenses.length > 0 && (
                <ExpenseSelectionSection
                  expenses={h.unbilledData.unbilledExpenses}
                  currency={h.currency}
                  selectedExpenseIds={h.selectedExpenseIds}
                  onToggleExpense={h.handleToggleExpense}
                  onToggleAll={h.handleToggleAllExpenses}
                />
              )}

              {/* Running total */}
              <div className="flex items-center justify-between border-t border-slate-200 pt-3 dark:border-slate-800">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  Total ({h.totalItemCount} items)
                </span>
                <span className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                  {safeFormatCurrency(h.runningTotal, h.currency)}
                </span>
              </div>

              {/* Validation Checklist */}
              {h.validationChecks && h.validationChecks.length > 0 && (
                <div
                  data-testid="validation-checklist"
                  className="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/50"
                >
                  <p className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                    Pre-generation checks
                  </p>
                  <ul className="space-y-1">
                    {h.validationChecks.map((check, idx) => (
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

              {h.error && <p className="text-destructive text-sm">{h.error}</p>}

              <DialogFooter>
                <Button
                  type="button"
                  variant="plain"
                  onClick={() => h.handleOpenChange(false)}
                  disabled={h.isPending}
                >
                  Cancel
                </Button>
                {!h.validationChecks ? (
                  <Button
                    type="button"
                    onClick={h.handleRunValidation}
                    disabled={h.isPending || h.isValidating || h.totalItemCount === 0}
                  >
                    {h.isValidating ? "Validating..." : "Validate & Create Draft"}
                  </Button>
                ) : (
                  <Button
                    type="button"
                    onClick={h.handleCreateDraft}
                    disabled={h.isPending || h.totalItemCount === 0}
                  >
                    {h.isPending
                      ? "Creating..."
                      : h.validationChecks.some((c) => !c.passed)
                        ? `Create Draft (${h.validationChecks.filter((c) => !c.passed).length} issues)`
                        : "Create Draft"}
                  </Button>
                )}
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {h.prereqModalOpen && (
        <PrerequisiteModal
          open={h.prereqModalOpen}
          onOpenChange={h.setPrereqModalOpen}
          context="INVOICE_GENERATION"
          violations={h.prereqViolations}
          entityType="CUSTOMER"
          entityId={customerId}
          slug={slug}
          onResolved={() => {
            h.setPrereqModalOpen(false);
            h.handleOpenChange(true);
          }}
        />
      )}
    </>
  );
}

// --- Sub-components (kept in same file as they're small and tightly coupled) ---

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
  const selectableEntries = project.entries.filter((e) => e.billingRateCurrency === currency);
  const allSelected =
    selectableEntries.length > 0 && selectableEntries.every((e) => selectedEntryIds.has(e.id));

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
    selectableExpenses.length > 0 && selectableExpenses.every((e) => selectedExpenseIds.has(e.id));

  const expensesByProject: Record<
    string,
    { projectName: string; expenses: UnbilledExpenseEntry[] }
  > = {};
  for (const expense of expenses) {
    if (!expensesByProject[expense.projectId]) {
      expensesByProject[expense.projectId] = { projectName: expense.projectName, expenses: [] };
    }
    expensesByProject[expense.projectId].expenses.push(expense);
  }

  return (
    <div data-testid="expense-selection-section">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300">Expenses</h3>
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
                      className={cn(
                        "flex items-center gap-3 px-4 py-2 text-sm",
                        currencyMismatch
                          ? "cursor-not-allowed opacity-50"
                          : "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50",
                      )}
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
                            <span className="text-xs text-slate-400">({expense.currency})</span>
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
                        {safeFormatCurrency(expense.billableAmount, expense.currency)}
                      </span>
                    </label>
                  );
                })}
              </div>
            </div>
          )
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
  const noRate = entry.billingRateCurrency == null;
  const currencyMismatch = !noRate && entry.billingRateCurrency !== currency;
  const disabled = noRate || currencyMismatch;

  return (
    <label
      className={cn(
        "flex items-center gap-3 px-4 py-2 text-sm",
        disabled
          ? "cursor-not-allowed opacity-50"
          : "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50",
      )}
    >
      <input
        type="checkbox"
        checked={isSelected}
        onChange={() => onToggle(entry.id)}
        disabled={disabled}
        className="size-4 rounded accent-teal-600"
      />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-medium text-slate-900 dark:text-slate-100">{entry.taskTitle}</span>
          {currencyMismatch && (
            <span className="text-xs text-slate-400">({entry.billingRateCurrency})</span>
          )}
          {entry.rateSource === "RESOLVED" && (
            <span
              className="rounded bg-teal-50 px-1.5 py-0.5 text-[10px] font-medium text-teal-700 dark:bg-teal-900/30 dark:text-teal-300"
              title="Rate resolved from current rate card (not snapshotted at log time)"
            >
              Rate card
            </span>
          )}
        </div>
        <div className="text-xs text-slate-500">
          {entry.memberName} &middot; {formatDate(entry.date)} &middot;{" "}
          {formatDuration(entry.durationMinutes)}
        </div>
      </div>
      <span className="shrink-0 text-right font-medium text-slate-700 dark:text-slate-300">
        {entry.billableValue != null && entry.billingRateCurrency
          ? formatCurrency(entry.billableValue, entry.billingRateCurrency)
          : "N/A"}
      </span>
    </label>
  );
}
