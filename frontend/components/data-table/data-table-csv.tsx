// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): views/datatables/datatable-product.tsx (exportToCSV, reached via views/datatables/export-button/index.tsx).
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import type { Table } from "@tanstack/react-table";
import { Download } from "lucide-react";
import { Button } from "@b2mash/ui/button";

/** UTF-8 byte-order mark: Excel needs it to read non-ASCII text (e.g. en-ZA names) as UTF-8. */
export const CSV_BOM = "﻿";

/** A string a spreadsheet would evaluate as a formula (OWASP CSV injection). */
const FORMULA_TRIGGER = /^[=+\-@\t\r\n]/;

/**
 * One CSV field. Strings that start with `=`, `+`, `-`, `@`, TAB, CR or LF get a leading `'` so a
 * spreadsheet shows them as text instead of evaluating them; numbers are written raw. Then RFC 4180:
 * a field containing `"`, `,`, CR or LF is wrapped in double quotes with inner quotes doubled.
 * `null`/`undefined` → empty field.
 */
export function toCsvField(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return "";
  let text = String(value);
  if (typeof value === "string" && FORMULA_TRIGGER.test(text)) text = `'${text}`;
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/**
 * The CSV `DataTable` exports: the visible columns that define `meta.csv`, in display order, ×
 * the selected rows when any are selected (selection spans pages), else the rendered rows (the
 * current page when paginated). Header = each column's `meta.label`. Records end with CRLF,
 * including the last one (RFC 4180 §2). No BOM: `downloadCsv` adds it.
 */
export function buildCsv<T>(table: Table<T>): string {
  const columns = table.getVisibleLeafColumns().flatMap((column) => {
    const meta = column.columnDef.meta;
    return meta?.csv ? [{ label: meta.label, csv: meta.csv }] : [];
  });
  const selected = table.getSelectedRowModel().rows;
  const rows = selected.length > 0 ? selected : table.getRowModel().rows;
  const records = [
    columns.map((column) => toCsvField(column.label)),
    ...rows.map((row) => columns.map((column) => toCsvField(column.csv(row.original)))),
  ];
  return records.map((fields) => fields.join(",")).join("\r\n") + "\r\n";
}

/**
 * Client-side download (no server round-trip). The anchor is attached for the click (Firefox ignores
 * a detached one) and the object URL is revoked on a later task: revoking it synchronously after
 * `click()` can cancel the download before the browser has read the blob.
 */
function downloadCsv(csv: string, filename: string) {
  const blob = new Blob([CSV_BOM, csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.hidden = true;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

interface DataTableCsvButtonProps<T> {
  table: Table<T>;
  filename: string;
  disabled?: boolean;
}

/** "Export CSV": the visible columns × the selected-or-rendered rows, built from data on the page. */
export function DataTableCsvButton<T>({ table, filename, disabled }: DataTableCsvButtonProps<T>) {
  return (
    <Button
      variant="outline"
      size="sm"
      disabled={disabled}
      onClick={() => downloadCsv(buildCsv(table), filename)}
    >
      <Download aria-hidden="true" />
      Export CSV
    </Button>
  );
}
