"use client";

import { useState } from "react";
import { Pencil, Trash2, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatCurrency } from "@/lib/format";
import type { InvoiceLineResponse } from "@/lib/types";

interface InvoiceLineTableProps {
  lines: InvoiceLineResponse[];
  currency: string;
  editable: boolean;
  onAddLine?: () => void;
  onEditLine?: (line: InvoiceLineResponse) => void;
  onDeleteLine?: (lineId: string) => void;
}

export function InvoiceLineTable({
  lines,
  currency,
  editable,
  onAddLine,
  onEditLine,
  onDeleteLine,
}: InvoiceLineTableProps) {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-olive-900 dark:text-olive-100">
          Line Items
        </h2>
        {editable && onAddLine && (
          <Button variant="outline" size="sm" onClick={onAddLine}>
            <Plus className="mr-1.5 size-4" />
            Add Line
          </Button>
        )}
      </div>

      {lines.length === 0 ? (
        <p className="py-8 text-center text-sm text-olive-500 dark:text-olive-400">
          No line items yet. Add a line item to get started.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-olive-200 dark:border-olive-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Description
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                  Project
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Qty
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Rate
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Amount
                </th>
                {editable && (
                  <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                    Actions
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {lines.map((line) => (
                <tr
                  key={line.id}
                  className="border-b border-olive-100 last:border-0 dark:border-olive-800/50"
                >
                  <td className="px-4 py-3 text-sm text-olive-900 dark:text-olive-100">
                    {line.description}
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-olive-600 sm:table-cell dark:text-olive-400">
                    {line.projectName || "\u2014"}
                  </td>
                  <td className="px-4 py-3 text-right text-sm text-olive-900 dark:text-olive-100">
                    {line.quantity}
                  </td>
                  <td className="px-4 py-3 text-right text-sm text-olive-900 dark:text-olive-100">
                    {formatCurrency(line.unitPrice, currency)}
                  </td>
                  <td className="px-4 py-3 text-right text-sm font-medium text-olive-900 dark:text-olive-100">
                    {formatCurrency(line.amount, currency)}
                  </td>
                  {editable && (
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {onEditLine && (
                          <button
                            type="button"
                            onClick={() => onEditLine(line)}
                            className="rounded p-1 text-olive-500 transition-colors hover:bg-olive-100 hover:text-olive-700 dark:hover:bg-olive-800 dark:hover:text-olive-300"
                            aria-label={`Edit ${line.description}`}
                          >
                            <Pencil className="size-4" />
                          </button>
                        )}
                        {onDeleteLine && (
                          <button
                            type="button"
                            onClick={() => onDeleteLine(line.id)}
                            className="rounded p-1 text-olive-500 transition-colors hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950 dark:hover:text-red-400"
                            aria-label={`Delete ${line.description}`}
                          >
                            <Trash2 className="size-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
