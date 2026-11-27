"use client";

import { useMemo, type ReactNode } from "react";
import { DataTable, type DataTableToolbarSlots } from "@/components/data-table/data-table";
import type { InvoiceResponse } from "@/lib/types";
import { buildFeeNoteColumns } from "./fee-note-columns";

interface FeeNotesTableProps {
  slug: string;
  /** The listed fee notes, already filtered by the server page (`status`, `customerId`). */
  invoices: InvoiceResponse[];
  /** Pre-rendered toolbar slots (the status filter links) from the server page. */
  toolbar?: DataTableToolbarSlots;
  /** Pre-rendered empty state, shown instead of the table. */
  empty: ReactNode;
}

const getInvoiceRowId = (row: InvoiceResponse) => row.id;

/**
 * Fee notes list on `DataTable` (Phase 91 666A, ADR-369 client mode). The server page fetches and
 * filters; this client wrapper only receives serializable props and builds the column defs and
 * `rowHref` itself (OQ #19). Every row renders (no pager, as before); CSV exports the rendered rows.
 */
export function FeeNotesTable({ slug, invoices, toolbar, empty }: FeeNotesTableProps) {
  const columns = useMemo(() => buildFeeNoteColumns(slug), [slug]);

  return (
    <DataTable
      columns={columns}
      data={invoices}
      getRowId={getInvoiceRowId}
      rowHref={(row) => `/org/${slug}/invoices/${row.id}`}
      toolbar={toolbar}
      empty={empty}
      pageSize={null}
      columnVisibility={false}
      stickyHeader={false}
      exportCsv={{ filename: "fee-notes.csv" }}
    />
  );
}
