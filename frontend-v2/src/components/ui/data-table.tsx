"use client";

import * as React from "react";
import {
  type ColumnDef,
  type SortingState,
  type OnChangeFn,
  type RowSelectionState,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table";
import { ChevronUp, ChevronDown, ChevronsUpDown } from "lucide-react";

import { cn } from "@/lib/utils";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { DataTableSkeleton } from "@/components/ui/data-table-skeleton";

interface DataTableProps<TData> {
  columns: ColumnDef<TData, unknown>[];
  data: TData[];
  onRowClick?: (row: TData) => void;
  isLoading?: boolean;
  emptyState?: React.ReactNode;
  sorting?: SortingState;
  onSortingChange?: OnChangeFn<SortingState>;
  enableRowSelection?: boolean;
  rowSelection?: RowSelectionState;
  onRowSelectionChange?: OnChangeFn<RowSelectionState>;
}

function getSelectColumn<TData>(): ColumnDef<TData, unknown> {
  return {
    id: "select",
    header: ({ table }) => (
      <Checkbox
        checked={
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && "indeterminate")
        }
        onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
        aria-label="Select all"
      />
    ),
    cell: ({ row }) => (
      <Checkbox
        checked={row.getIsSelected()}
        onCheckedChange={(value) => row.toggleSelected(!!value)}
        aria-label="Select row"
        onClick={(e) => e.stopPropagation()}
      />
    ),
    enableSorting: false,
    size: 40,
  };
}

function DataTable<TData>({
  columns,
  data,
  onRowClick,
  isLoading,
  emptyState,
  sorting: externalSorting,
  onSortingChange: externalOnSortingChange,
  enableRowSelection = false,
  rowSelection: externalRowSelection,
  onRowSelectionChange,
}: DataTableProps<TData>) {
  const [internalSorting, setInternalSorting] = React.useState<SortingState>(
    []
  );
  const [internalRowSelection, setInternalRowSelection] =
    React.useState<RowSelectionState>({});

  const sorting = externalSorting ?? internalSorting;
  const onSortingChange = externalOnSortingChange ?? setInternalSorting;
  const rowSelection = externalRowSelection ?? internalRowSelection;
  const rowSelectionChange = onRowSelectionChange ?? setInternalRowSelection;

  const allColumns = React.useMemo(() => {
    if (!enableRowSelection) return columns;
    return [getSelectColumn<TData>(), ...columns];
  }, [columns, enableRowSelection]);

  const table = useReactTable({
    data,
    columns: allColumns,
    state: {
      sorting,
      rowSelection,
    },
    onSortingChange,
    onRowSelectionChange: rowSelectionChange,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: externalOnSortingChange
      ? undefined
      : getSortedRowModel(),
    enableRowSelection,
    manualSorting: !!externalOnSortingChange,
  });

  if (isLoading) {
    return <DataTableSkeleton columnCount={allColumns.length} />;
  }

  if (data.length === 0 && emptyState) {
    return <>{emptyState}</>;
  }

  return (
    <Table>
      <TableHeader className="sticky top-0 z-10 bg-white dark:bg-slate-950">
        {table.getHeaderGroups().map((headerGroup) => (
          <TableRow key={headerGroup.id}>
            {headerGroup.headers.map((header) => (
              <TableHead key={header.id} style={{ width: header.getSize() !== 150 ? header.getSize() : undefined }}>
                {header.isPlaceholder ? null : header.column.getCanSort() ? (
                  <button
                    type="button"
                    className={cn(
                      "inline-flex items-center gap-1 hover:text-foreground transition-colors",
                      header.column.getIsSorted() && "text-foreground"
                    )}
                    onClick={header.column.getToggleSortingHandler()}
                  >
                    {flexRender(
                      header.column.columnDef.header,
                      header.getContext()
                    )}
                    {header.column.getIsSorted() === "asc" ? (
                      <ChevronUp className="size-3.5" />
                    ) : header.column.getIsSorted() === "desc" ? (
                      <ChevronDown className="size-3.5" />
                    ) : (
                      <ChevronsUpDown className="size-3.5 opacity-50" />
                    )}
                  </button>
                ) : (
                  flexRender(
                    header.column.columnDef.header,
                    header.getContext()
                  )
                )}
              </TableHead>
            ))}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {table.getRowModel().rows.length === 0 ? (
          <TableRow>
            <TableCell
              colSpan={allColumns.length}
              className="h-24 text-center"
            >
              No results.
            </TableCell>
          </TableRow>
        ) : (
          table.getRowModel().rows.map((row) => (
            <TableRow
              key={row.id}
              data-state={row.getIsSelected() ? "selected" : undefined}
              className={cn(
                "hover:bg-slate-50 dark:hover:bg-slate-900",
                onRowClick && "cursor-pointer"
              )}
              onClick={
                onRowClick ? () => onRowClick(row.original) : undefined
              }
            >
              {row.getVisibleCells().map((cell) => (
                <TableCell key={cell.id}>
                  {flexRender(
                    cell.column.columnDef.cell,
                    cell.getContext()
                  )}
                </TableCell>
              ))}
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}

export { DataTable, type DataTableProps };
