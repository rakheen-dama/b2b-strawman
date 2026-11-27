"use client";

import { Button } from "@b2mash/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type {
  UnbilledTimeEntry,
  UnbilledExpense,
  UnbilledDisbursement,
} from "@/lib/api/billing-runs";

interface CustomerDetailData {
  timeEntries: UnbilledTimeEntry[];
  expenses: UnbilledExpense[];
  // OBS-2104c — third entry type rendered in its own table below Expenses.
  disbursements: UnbilledDisbursement[];
  includedTimeIds: Set<string>;
  includedExpenseIds: Set<string>;
  includedDisbursementIds: Set<string>;
  isLoading: boolean;
  isLoaded: boolean;
  error: string | null;
}

interface CherryPickCustomerDetailProps {
  itemId: string;
  isExcluded: boolean;
  data: CustomerDetailData | undefined;
  currency: string;
  subtotal: number;
  onExclude: (itemId: string) => void;
  onInclude: (itemId: string) => void;
  onToggleTimeEntry: (itemId: string, entryId: string) => void;
  onToggleExpenseEntry: (itemId: string, entryId: string) => void;
  onToggleDisbursementEntry: (itemId: string, entryId: string) => void;
}

export function CherryPickCustomerDetail({
  itemId,
  isExcluded,
  data,
  currency,
  subtotal,
  onExclude,
  onInclude,
  onToggleTimeEntry,
  onToggleExpenseEntry,
  onToggleDisbursementEntry,
}: CherryPickCustomerDetailProps) {
  return (
    <div className="border-t border-slate-200 p-4 dark:border-slate-700">
      {/* Exclude/Include Button */}
      <div className="mb-4 flex justify-end">
        {isExcluded ? (
          <Button variant="outline" size="sm" onClick={() => onInclude(itemId)}>
            Include Client
          </Button>
        ) : (
          <Button variant="outline" size="sm" onClick={() => onExclude(itemId)}>
            Exclude Client
          </Button>
        )}
      </div>

      {data?.isLoading && (
        <div className="space-y-2">
          <div className="h-8 w-full animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
          <div className="h-8 w-full animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
        </div>
      )}

      {data?.error && (
        <p role="alert" className="text-destructive text-sm">
          {data.error}
        </p>
      )}

      {data?.isLoaded && !isExcluded && (
        <>
          {/* Time Entries Table */}
          {data.timeEntries.length > 0 && (
            <div className="mb-4">
              <h3 className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                Time Entries
              </h3>
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead>Include</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead>Member</TableHead>
                    <TableHead>Task</TableHead>
                    <TableHead>Hours</TableHead>
                    <TableHead className="text-right">Rate</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.timeEntries.map((entry) => (
                    <TableRow key={entry.id}>
                      <TableCell>
                        <Checkbox
                          checked={data.includedTimeIds.has(entry.id)}
                          onCheckedChange={() => onToggleTimeEntry(itemId, entry.id)}
                          aria-label={`Include time entry ${entry.description ?? entry.id}`}
                        />
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatLocalDate(entry.date)}
                      </TableCell>
                      <TableCell className="text-muted-foreground">Member</TableCell>
                      <TableCell className="text-muted-foreground whitespace-normal">
                        {entry.description ?? "Task"}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {(entry.durationMinutes / 60).toFixed(1)}
                      </TableCell>
                      <TableCell className="text-muted-foreground text-right">
                        {entry.billingRateSnapshot != null
                          ? formatCurrency(entry.billingRateSnapshot, currency)
                          : "N/A"}
                      </TableCell>
                      <TableCell className="text-right font-medium">
                        {entry.billableValue != null
                          ? formatCurrency(entry.billableValue, currency)
                          : "N/A"}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {/* Expenses Table — legacy expenses, labelled "Other Disbursements" beside the legal
              Disbursements table (matches the fee-note line table). */}
          {data.expenses.length > 0 && (
            <div>
              <h3 className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                Other Disbursements
              </h3>
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead>Include</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead>Description</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.expenses.map((expense) => (
                    <TableRow key={expense.id}>
                      <TableCell>
                        <Checkbox
                          checked={data.includedExpenseIds.has(expense.id)}
                          onCheckedChange={() => onToggleExpenseEntry(itemId, expense.id)}
                          aria-label={`Include expense ${expense.description ?? expense.id}`}
                        />
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatLocalDate(expense.date)}
                      </TableCell>
                      <TableCell className="text-muted-foreground whitespace-normal">
                        {expense.description ?? "—"}
                      </TableCell>
                      <TableCell className="text-muted-foreground">{expense.category}</TableCell>
                      <TableCell className="text-right font-medium">
                        {formatCurrency(expense.billableAmount, currency)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {/* Disbursements Table — OBS-2104c. Mirrors the Expenses table shape; columns are
              tuned to the legal-vertical disbursement model (incurredDate / category /
              supplierName) rather than the generic Expense model. */}
          {data.disbursements.length > 0 && (
            <div className="mt-4">
              <h3 className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                Disbursements
              </h3>
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead>Include</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead>Description</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead>Supplier</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.disbursements.map((disbursement) => (
                    <TableRow key={disbursement.id}>
                      <TableCell>
                        <Checkbox
                          checked={data.includedDisbursementIds.has(disbursement.id)}
                          onCheckedChange={() => onToggleDisbursementEntry(itemId, disbursement.id)}
                          aria-label={`Include disbursement ${disbursement.description}`}
                        />
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatLocalDate(disbursement.incurredDate)}
                      </TableCell>
                      <TableCell className="text-muted-foreground whitespace-normal">
                        {disbursement.description}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {disbursement.category}
                      </TableCell>
                      <TableCell className="text-muted-foreground whitespace-normal">
                        {disbursement.supplierName}
                      </TableCell>
                      <TableCell className="text-right font-medium">
                        {formatCurrency(disbursement.billableAmount, currency)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {data.timeEntries.length === 0 &&
            data.expenses.length === 0 &&
            data.disbursements.length === 0 && (
              <p className="text-sm text-slate-500 dark:text-slate-400">
                No unbilled entries for this client.
              </p>
            )}

          {/* Subtotal */}
          <div className="mt-3 flex justify-end border-t border-slate-200 pt-3 dark:border-slate-700">
            <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
              Subtotal: {formatCurrency(subtotal, currency)}
            </span>
          </div>
        </>
      )}
    </div>
  );
}
