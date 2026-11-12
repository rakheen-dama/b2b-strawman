"use client";

// Chart kit "View as table" (ADR-370 accessibility contract). The <table> is mounted only while
// open — never hidden with CSS — so pages with a strict getByRole("table") keep one table.
import { useId, useState } from "react";
import { Button } from "@b2mash/ui/button";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

export interface ChartTableColumn {
  key: string;
  label: string;
  /** Right-aligned, monospaced figures. */
  numeric?: boolean;
}

export interface ChartTableRow {
  id: string;
  /** One pre-formatted cell per column; the first is the row header. */
  cells: React.ReactNode[];
}

interface ChartTableToggleProps {
  /** sr-only table caption (the chart title). */
  caption: string;
  columns: ChartTableColumn[];
  rows: ChartTableRow[];
}

export function ChartTableToggle({ caption, columns, rows }: ChartTableToggleProps) {
  const [open, setOpen] = useState(false);
  const panelId = useId();

  return (
    <div data-slot="chart-table-toggle" className="mt-3 flex flex-col gap-2">
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="self-end"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        onClick={() => setOpen((value) => !value)}
      >
        {open ? "Hide table" : "View as table"}
      </Button>
      {open ? (
        <div id={panelId} data-slot="chart-table">
          <Table>
            <TableCaption className="sr-only">{caption}</TableCaption>
            <TableHeader>
              <TableRow>
                {columns.map((column) => (
                  <TableHead
                    key={column.key}
                    scope="col"
                    className={cn(column.numeric && "text-right")}
                  >
                    {column.label}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id}>
                  {row.cells.map((cell, index) =>
                    index === 0 ? (
                      <th
                        key={columns[index]?.key ?? index}
                        scope="row"
                        className="text-foreground px-2 py-2 text-left font-medium"
                      >
                        {cell}
                      </th>
                    ) : (
                      <TableCell
                        key={columns[index]?.key ?? index}
                        className={cn(
                          columns[index]?.numeric && "text-right font-mono tabular-nums"
                        )}
                      >
                        {cell}
                      </TableCell>
                    )
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      ) : null}
    </div>
  );
}
