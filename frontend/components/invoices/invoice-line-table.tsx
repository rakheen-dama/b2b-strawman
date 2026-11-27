"use client";

import { ListPlus, Pencil, Trash2, Plus } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Badge } from "@b2mash/ui/badge";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { InvoiceLineResponse } from "@/lib/types";

/**
 * K-74 / F-666B-A1/A2 floors for the two wrapping columns (kit cells are whitespace-nowrap, D-55),
 * with px-3 cells: measured on the live stack, the draft table (with Actions) fits its 710 px frame
 * at 1024, Description wraps onto no more lines than before K-74 and Tax stays on 2 lines.
 */
const DESCRIPTION_CLASS = "min-w-40 whitespace-normal";
const TAX_CLASS = "min-w-32 whitespace-normal";

const SECTION_LABELS: Record<string, string> = {
  TIME: "Time Entries",
  EXPENSE: "Other Disbursements",
  DISBURSEMENT: "Disbursements",
  TARIFF: "Tariff Items",
  OTHER: "Other",
};

/** Groups lines into ordered sections by lineType. */
function groupLinesByType(lines: InvoiceLineResponse[]) {
  const groups: Record<string, InvoiceLineResponse[]> = {};
  for (const line of lines) {
    // Backward compat: treat missing lineType as TIME
    const type = line.lineType ?? "TIME";
    const groupKey: string =
      type === "TARIFF"
        ? "TARIFF"
        : type === "DISBURSEMENT"
          ? "DISBURSEMENT"
          : type === "MANUAL" || type === "FIXED_FEE"
            ? "OTHER"
            : type;
    if (!groups[groupKey]) {
      groups[groupKey] = [];
    }
    groups[groupKey].push(line);
  }
  // Return in display order: TIME, EXPENSE, DISBURSEMENT, TARIFF, OTHER
  const ordered: { key: string; label: string; lines: InvoiceLineResponse[] }[] = [];
  for (const key of ["TIME", "EXPENSE", "DISBURSEMENT", "TARIFF", "OTHER"]) {
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
  onDeleteLine?: (lineId: string) => void
) {
  const sections = groupLinesByType(lines);
  const needsSectionHeaders = sections.length > 1;
  const colCount = 5 + (hasPerLineTax ? 1 : 0) + (editable ? 1 : 0);

  return sections.map((section) => (
    <LineSection
      key={section.key}
      type={section.key}
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
  type,
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
  /** Section key (TIME / EXPENSE / DISBURSEMENT / TARIFF / OTHER) — keys the test id, not the label. */
  type: string;
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
        <TableRow data-testid={`section-header-${type}`} className="hover:bg-transparent">
          <TableCell
            colSpan={colCount}
            className="bg-muted/50 text-muted-foreground px-3 py-2 text-xs font-semibold tracking-wide uppercase"
          >
            {label}
          </TableCell>
        </TableRow>
      )}
      {lines.map((line) => (
        <TableRow key={line.id}>
          {/* K-74: a floor for the description so it no longer wraps onto 6 lines at 1024. */}
          <TableCell className={cn(DESCRIPTION_CLASS, "text-foreground px-3 py-3")}>
            <span className="flex items-center gap-2">
              {line.description}
              {line.lineSource === "TARIFF" && (
                <Badge variant="neutral" className="text-xs">
                  Tariff
                </Badge>
              )}
            </span>
          </TableCell>
          <TableCell className="text-muted-foreground hidden px-3 py-3 whitespace-normal sm:table-cell">
            {line.matterName || "\u2014"}
          </TableCell>
          <TableCell className="text-foreground px-3 py-3 text-right">{line.quantity}</TableCell>
          <TableCell className="text-foreground px-3 py-3 text-right">
            {formatCurrency(line.unitPrice, currency)}
          </TableCell>
          <TableCell className="text-foreground px-3 py-3 text-right font-medium">
            {formatCurrency(line.amount, currency)}
          </TableCell>
          {hasPerLineTax && (
            <TableCell className={cn(TAX_CLASS, "text-muted-foreground px-3 py-3 text-right")}>
              {line.taxExempt ? (
                <span>Exempt</span>
              ) : line.taxRateName && line.taxAmount != null ? (
                <span>
                  {line.taxRateName} ({line.taxRatePercent}%){" "}
                  {formatCurrency(line.taxAmount, currency)}
                </span>
              ) : (
                "\u2014"
              )}
            </TableCell>
          )}
          {editable && (
            <TableCell className="px-3 py-3 text-right">
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
            </TableCell>
          )}
        </TableRow>
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
        <h2 className="font-display text-foreground text-lg">Line Items</h2>
        {editable && onAddLine && (
          <Button variant="outline" size="sm" onClick={onAddLine}>
            <Plus className="mr-1.5 size-4" />
            Add Line
          </Button>
        )}
      </div>

      {lines.length === 0 ? (
        <EmptyState
          variant="inline"
          icon={ListPlus}
          title="No line items yet. Add a line item to get started."
          description={null}
        />
      ) : (
        <div className="bg-card rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className={cn(DESCRIPTION_CLASS, "px-3")}>Description</TableHead>
                <TableHead className="hidden px-3 sm:table-cell">Matter</TableHead>
                <TableHead className="px-3 text-right">Qty</TableHead>
                <TableHead className="px-3 text-right">Rate</TableHead>
                <TableHead className="px-3 text-right">Amount</TableHead>
                {hasPerLineTax && (
                  <TableHead className={cn(TAX_CLASS, "px-3 text-right")}>Tax</TableHead>
                )}
                {editable && <TableHead className="px-3 text-right">Actions</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {renderGroupedLines(
                lines,
                currency,
                hasPerLineTax,
                editable,
                onEditLine,
                onDeleteLine
              )}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
