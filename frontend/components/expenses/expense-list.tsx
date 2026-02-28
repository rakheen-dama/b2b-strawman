"use client";

import { useState, useTransition } from "react";
import { Pencil, Receipt, RotateCcw, Trash2, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { ExpenseCategoryBadge, CATEGORY_LABELS } from "@/components/expenses/expense-category-badge";
import { LogExpenseDialog } from "@/components/expenses/log-expense-dialog";
import { formatCurrencySafe, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  deleteExpense,
  writeOffExpense,
  restoreExpense,
} from "@/app/(app)/org/[slug]/projects/[id]/expense-actions";
import type {
  ExpenseResponse,
  ExpenseCategory,
  ExpenseBillingStatus,
} from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";

const ADMIN_OR_OWNER_ROLES = new Set(["org:admin", "org:owner"]);

type BillingStatusFilter = "all" | "UNBILLED" | "BILLED" | "NON_BILLABLE";

const BILLING_STATUS_FILTER_OPTIONS: {
  key: BillingStatusFilter;
  label: string;
}[] = [
  { key: "all", label: "All" },
  { key: "UNBILLED", label: "Unbilled" },
  { key: "BILLED", label: "Billed" },
  { key: "NON_BILLABLE", label: "Non-billable" },
];

const EXPENSE_CATEGORIES: ExpenseCategory[] = [
  "FILING_FEE",
  "TRAVEL",
  "COURIER",
  "SOFTWARE",
  "SUBCONTRACTOR",
  "PRINTING",
  "COMMUNICATION",
  "OTHER",
];

interface ExpenseListProps {
  expenses: ExpenseResponse[];
  slug: string;
  projectId: string;
  tasks: { id: string; title: string }[];
  members: { id: string; name: string }[];
  currentMemberId: string | null;
  orgRole: string | null;
}

function ExpenseBillingBadge({
  billingStatus,
}: {
  billingStatus: ExpenseBillingStatus;
}) {
  switch (billingStatus) {
    case "BILLED":
      return <Badge variant="success">Billed</Badge>;
    case "UNBILLED":
      return <Badge variant="neutral">Unbilled</Badge>;
    case "NON_BILLABLE":
      return null;
  }
}

export function ExpenseList({
  expenses,
  slug,
  projectId,
  tasks,
  members,
  currentMemberId,
  orgRole,
}: ExpenseListProps) {
  const [billingFilter, setBillingFilter] =
    useState<BillingStatusFilter>("all");
  const [categoryFilter, setCategoryFilter] = useState<
    ExpenseCategory | "all"
  >("all");
  const [memberFilter, setMemberFilter] = useState<string>("all");
  const [isPending, startTransition] = useTransition();

  const isElevated = orgRole ? ADMIN_OR_OWNER_ROLES.has(orgRole) : false;
  const isAdminOrOwner = isElevated;

  // Apply client-side filters
  const filteredExpenses = expenses.filter((e) => {
    if (billingFilter !== "all" && e.billingStatus !== billingFilter)
      return false;
    if (categoryFilter !== "all" && e.category !== categoryFilter) return false;
    if (memberFilter !== "all" && e.memberId !== memberFilter) return false;
    return true;
  });

  const totalAmount = filteredExpenses.reduce((sum, e) => sum + e.amount, 0);

  function canEditExpense(expense: ExpenseResponse): boolean {
    if (expense.billingStatus === "BILLED") return false;
    if (isElevated) return true;
    if (currentMemberId && expense.memberId === currentMemberId) return true;
    return false;
  }

  function canDeleteExpense(expense: ExpenseResponse): boolean {
    return canEditExpense(expense);
  }

  function handleDelete(expenseId: string) {
    startTransition(async () => {
      await deleteExpense(slug, projectId, expenseId);
    });
  }

  function handleWriteOff(expenseId: string) {
    startTransition(async () => {
      await writeOffExpense(slug, projectId, expenseId);
    });
  }

  function handleRestore(expenseId: string) {
    startTransition(async () => {
      await restoreExpense(slug, projectId, expenseId);
    });
  }

  if (expenses.length === 0) {
    return (
      <EmptyState
        icon={Receipt}
        title="No expenses logged"
        description="Log disbursements like filing fees, travel, and courier costs against this project."
      />
    );
  }

  const showActionsColumn =
    filteredExpenses.some(
      (e) =>
        canEditExpense(e) ||
        canDeleteExpense(e) ||
        (isAdminOrOwner && e.billingStatus === "UNBILLED") ||
        (isAdminOrOwner && e.billingStatus === "NON_BILLABLE"),
    );

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            Expenses
          </h3>
          <Badge variant="neutral">
            {filteredExpenses.length}{" "}
            {filteredExpenses.length === 1 ? "expense" : "expenses"}
          </Badge>
        </div>
      </div>

      {/* Billing status filter */}
      <div className="flex flex-wrap gap-2" role="group" aria-label="Billing status filter">
        {BILLING_STATUS_FILTER_OPTIONS.map((option) => (
          <button
            key={option.key}
            type="button"
            onClick={() => setBillingFilter(option.key)}
            className={cn(
              "rounded-full px-3 py-1 text-sm font-medium transition-colors",
              billingFilter === option.key
                ? "bg-slate-900 text-slate-50 dark:bg-slate-100 dark:text-slate-900"
                : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700",
            )}
          >
            {option.label}
          </button>
        ))}
      </div>

      {/* Category filter */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
          Category:
        </span>
        <button
          type="button"
          onClick={() => setCategoryFilter("all")}
          className={cn(
            "rounded-full px-3 py-1 text-sm font-medium transition-colors",
            categoryFilter === "all"
              ? "bg-slate-900 text-slate-50 dark:bg-slate-100 dark:text-slate-900"
              : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700",
          )}
        >
          All
        </button>
        {EXPENSE_CATEGORIES.map((cat) => (
          <button
            key={cat}
            type="button"
            onClick={() => setCategoryFilter(cat)}
            className={cn(
              "rounded-full px-3 py-1 text-sm font-medium transition-colors",
              categoryFilter === cat
                ? "bg-slate-900 text-slate-50 dark:bg-slate-100 dark:text-slate-900"
                : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700",
            )}
          >
            {CATEGORY_LABELS[cat]}
          </button>
        ))}
      </div>

      {/* Member filter */}
      {members.length > 0 && (
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Member:
          </span>
          <select
            value={memberFilter}
            onChange={(e) => setMemberFilter(e.target.value)}
            className="rounded-md border border-slate-200 bg-white px-2 py-1 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300"
          >
            <option value="all">All Members</option>
            {members.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </select>
        </div>
      )}

      {filteredExpenses.length === 0 ? (
        <EmptyState
          icon={Receipt}
          title="No matching expenses"
          description="Try a different filter combination."
        />
      ) : (
        <div className="rounded-lg border border-slate-200 dark:border-slate-800">
          <Table>
            <TableHeader>
              <TableRow className="border-slate-200 hover:bg-transparent dark:border-slate-800">
                <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Date
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Description
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Category
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Amount
                </TableHead>
                <TableHead className="hidden text-xs uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
                  Billable Amount
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Billing
                </TableHead>
                <TableHead className="hidden text-xs uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
                  Member
                </TableHead>
                {showActionsColumn && (
                  <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Actions
                  </TableHead>
                )}
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredExpenses.map((expense) => {
                const editable = canEditExpense(expense);
                const deletable = canDeleteExpense(expense);
                const isBilled = expense.billingStatus === "BILLED";

                return (
                  <TableRow
                    key={expense.id}
                    className="border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900"
                  >
                    <TableCell className="text-sm text-slate-600 dark:text-slate-400">
                      {formatDate(expense.date)}
                    </TableCell>
                    <TableCell className="max-w-[200px] truncate text-sm font-medium text-slate-950 dark:text-slate-50">
                      {expense.description}
                    </TableCell>
                    <TableCell>
                      <ExpenseCategoryBadge category={expense.category} />
                    </TableCell>
                    <TableCell className="text-sm text-slate-600 dark:text-slate-400">
                      {formatCurrencySafe(expense.amount, expense.currency)}
                    </TableCell>
                    <TableCell className="hidden text-sm text-slate-600 sm:table-cell dark:text-slate-400">
                      {expense.billable ? (
                        <span className="font-medium text-slate-700 dark:text-slate-300">
                          {formatCurrencySafe(
                            expense.billableAmount,
                            expense.currency,
                          )}
                        </span>
                      ) : (
                        "\u2014"
                      )}
                    </TableCell>
                    <TableCell>
                      <ExpenseBillingBadge
                        billingStatus={expense.billingStatus}
                      />
                    </TableCell>
                    <TableCell className="hidden text-sm text-slate-600 sm:table-cell dark:text-slate-400">
                      {expense.memberName ?? "\u2014"}
                    </TableCell>
                    {showActionsColumn && (
                      <TableCell>
                        <TooltipProvider>
                          <div className="flex items-center gap-1">
                            {isBilled ? (
                              <>
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <span>
                                      <Button
                                        size="xs"
                                        variant="ghost"
                                        disabled
                                      >
                                        <Pencil className="size-3" />
                                      </Button>
                                    </span>
                                  </TooltipTrigger>
                                  <TooltipContent>
                                    <p>Part of invoice — cannot edit.</p>
                                  </TooltipContent>
                                </Tooltip>
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <span>
                                      <Button
                                        size="xs"
                                        variant="ghost"
                                        disabled
                                      >
                                        <Trash2 className="size-3" />
                                      </Button>
                                    </span>
                                  </TooltipTrigger>
                                  <TooltipContent>
                                    <p>Part of invoice — cannot delete.</p>
                                  </TooltipContent>
                                </Tooltip>
                              </>
                            ) : (
                              <>
                                {editable && (
                                  <LogExpenseDialog
                                    slug={slug}
                                    projectId={projectId}
                                    tasks={tasks}
                                    expenseToEdit={expense}
                                  >
                                    <Button size="xs" variant="ghost">
                                      <Pencil className="size-3" />
                                    </Button>
                                  </LogExpenseDialog>
                                )}
                                {deletable && (
                                  <AlertDialog>
                                    <AlertDialogTrigger asChild>
                                      <Button size="xs" variant="ghost">
                                        <Trash2 className="size-3" />
                                      </Button>
                                    </AlertDialogTrigger>
                                    <AlertDialogContent>
                                      <AlertDialogHeader>
                                        <AlertDialogTitle>
                                          Delete expense?
                                        </AlertDialogTitle>
                                        <AlertDialogDescription>
                                          This will permanently delete the
                                          expense &quot;{expense.description}
                                          &quot;. This action cannot be undone.
                                        </AlertDialogDescription>
                                      </AlertDialogHeader>
                                      <AlertDialogFooter>
                                        <AlertDialogCancel>
                                          Cancel
                                        </AlertDialogCancel>
                                        <AlertDialogAction
                                          onClick={() =>
                                            handleDelete(expense.id)
                                          }
                                          disabled={isPending}
                                        >
                                          {isPending
                                            ? "Deleting..."
                                            : "Delete"}
                                        </AlertDialogAction>
                                      </AlertDialogFooter>
                                    </AlertDialogContent>
                                  </AlertDialog>
                                )}
                                {isAdminOrOwner &&
                                  expense.billingStatus === "UNBILLED" && (
                                    <Tooltip>
                                      <TooltipTrigger asChild>
                                        <Button
                                          size="xs"
                                          variant="ghost"
                                          onClick={() =>
                                            handleWriteOff(expense.id)
                                          }
                                          disabled={isPending}
                                        >
                                          <XCircle className="size-3" />
                                        </Button>
                                      </TooltipTrigger>
                                      <TooltipContent>
                                        <p>Write off (mark non-billable)</p>
                                      </TooltipContent>
                                    </Tooltip>
                                  )}
                                {isAdminOrOwner &&
                                  expense.billingStatus === "NON_BILLABLE" && (
                                    <Tooltip>
                                      <TooltipTrigger asChild>
                                        <Button
                                          size="xs"
                                          variant="ghost"
                                          onClick={() =>
                                            handleRestore(expense.id)
                                          }
                                          disabled={isPending}
                                        >
                                          <RotateCcw className="size-3" />
                                        </Button>
                                      </TooltipTrigger>
                                      <TooltipContent>
                                        <p>Restore (mark billable)</p>
                                      </TooltipContent>
                                    </Tooltip>
                                  )}
                              </>
                            )}
                          </div>
                        </TooltipProvider>
                      </TableCell>
                    )}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>

          {/* Total row */}
          <div className="border-t border-slate-200 px-4 py-2 text-sm dark:border-slate-800">
            <span className="font-medium text-slate-700 dark:text-slate-300">
              Total: {formatCurrencySafe(totalAmount, filteredExpenses[0]?.currency ?? "ZAR")}
            </span>
            <span className="ml-2 text-slate-500 dark:text-slate-400">
              ({filteredExpenses.length}{" "}
              {filteredExpenses.length === 1 ? "expense" : "expenses"})
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
