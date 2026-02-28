"use client";

import { Pencil, Trash2, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatCurrency } from "@/lib/format";
import type { InvoiceLineResponse } from "@/lib/types";

const SECTION_LABELS: Record<string, string> = {
  TIME: "Time Entries",
  EXPENSE: "Expenses",
  OTHER: "Other",
};

/** Groups lines into ordered sections by lineType. */
function groupLinesByType(lines: InvoiceLineResponse[]) {
  const groups: Record<string, InvoiceLineResponse[]> = {};
  for (const line of lines) {
    // Backward compat: treat missing lineType as TIME
    const type = line.lineType ?? "TIME";
    const groupKey: string =
      type === "MANUAL" || type === "RETAINER" ? "OTHER" : type;
    if (!groups[groupKey]) {
      groups[groupKey] = [];
    }
    groups[groupKey].push(line);
  }
  // Return in display order: TIME, EXPENSE, OTHER
  const ordered: { key: string; label: string; lines: InvoiceLineResponse[] }[] = [];
  for (const key of ["TIME", "EXPENSE", "OTHER"]) {
    if (groups[key] && groups[key].length > 0) {
      ordered.push({ key, label: SECTION_LABELS[key], lines: groups[key] });
    }
  }
  return ordered;
}

function renderGroupedLines(
  lines: InvoiceLineResponse[],
  currency: string,
  hasPerLineTax: boolean,
  editable: boolean,
  onEditLine?: (line: InvoiceLineResponse) => void,
  onDeleteLine?: (lineId: string) => void,
) {
  const sections = groupLinesByType(lines);
  const needsSectionHeaders = sections.length > 1;
  const colCount = 5 + (hasPerLineTax ? 1 : 0) + (editable ? 1 : 0);

  return sections.map((section) => (
    <LineSection
      key={section.key}
      label={section.label}
      lines={section.lines}
      currency={currency}
      hasPerLineTax={hasPerLineTax}
      editable={editable}
      showHeader={needsSectionHeaders}
      colCount={colCount}
      onEditLine={onEditLine}
      onDeleteLine={onDeleteLine}
    />
  ));
}

function LineSection({
  label,
  lines,
  currency,
  hasPerLineTax,
  editable,
  showHeader,
  colCount,
  onEditLine,
  onDeleteLine,
}: {
  label: string;
  lines: InvoiceLineResponse[];
  currency: string;
  hasPerLineTax: boolean;
  editable: boolean;
  showHeader: boolean;
  colCount: number;
  onEditLine?: (line: InvoiceLineResponse) => void;
  onDeleteLine?: (lineId: string) => void;
}) {
  return (
    <>
      {showHeader && (
        <tr data-testid={`section-header-${label}`}>
          <td
            colSpan={colCount}
            className="bg-slate-50 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 dark:bg-slate-900/50 dark:text-slate-400"
          >
            {label}
          </td>
        </tr>
      )}
      {lines.map((line) => (
        <tr
          key={line.id}
          className="border-b border-slate-100 last:border-0 dark:border-slate-800/50"
        >
          <td className="px-4 py-3 text-sm text-slate-900 dark:text-slate-100">
            {line.description}
          </td>
          <td className="hidden px-4 py-3 text-sm text-slate-600 sm:table-cell dark:text-slate-400">
            {line.projectName || "\u2014"}
          </td>
          <td className="px-4 py-3 text-right text-sm text-slate-900 dark:text-slate-100">
            {line.quantity}
          </td>
          <td className="px-4 py-3 text-right text-sm text-slate-900 dark:text-slate-100">
            {formatCurrency(line.unitPrice, currency)}
          </td>
          <td className="px-4 py-3 text-right text-sm font-medium text-slate-900 dark:text-slate-100">
            {formatCurrency(line.amount, currency)}
          </td>
          {hasPerLineTax && (
            <td className="px-4 py-3 text-right text-sm text-slate-600 dark:text-slate-400">
              {line.taxExempt ? (
                <span className="text-slate-500 dark:text-slate-400">
                  Exempt
                </span>
              ) : line.taxRateName && line.taxAmount != null ? (
                <span>
                  {line.taxRateName} ({line.taxRatePercent}%)
                  {" "}
                  {formatCurrency(line.taxAmount, currency)}
                </span>
              ) : (
                "\u2014"
              )}
            </td>
          )}
          {editable && (
            <td className="px-4 py-3 text-right">
              <div className="flex items-center justify-end gap-1">
                {onEditLine && (
                  <button
                    type="button"
                    onClick={() => onEditLine(line)}
                    className="rounded p-1 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-slate-300"
                    aria-label={`Edit ${line.description}`}
                  >
                    <Pencil className="size-4" />
                  </button>
                )}
                {onDeleteLine && (
                  <button
                    type="button"
                    onClick={() => onDeleteLine(line.id)}
                    className="rounded p-1 text-slate-500 transition-colors hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950 dark:hover:text-red-400"
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
    </>
  );
}

interface InvoiceLineTableProps {
  lines: InvoiceLineResponse[];
  currency: string;
  editable: boolean;
  hasPerLineTax?: boolean;
  onAddLine?: () => void;
  onEditLine?: (line: InvoiceLineResponse) => void;
  onDeleteLine?: (lineId: string) => void;
}

export function InvoiceLineTable({
  lines,
  currency,
  editable,
  hasPerLineTax = false,
  onAddLine,
  onEditLine,
  onDeleteLine,
}: InvoiceLineTableProps) {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-slate-900 dark:text-slate-100">
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
        <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
          No line items yet. Add a line item to get started.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Description
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
                  Project
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Qty
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Rate
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Amount
                </th>
                {hasPerLineTax && (
                  <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Tax
                  </th>
                )}
                {editable && (
                  <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Actions
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {renderGroupedLines(lines, currency, hasPerLineTax, editable, onEditLine, onDeleteLine)}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
