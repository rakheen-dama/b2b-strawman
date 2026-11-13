"use client";

// Chart kit "View as table" (ADR-370 accessibility contract). The <table> is mounted only while
// open — never hidden with CSS — so pages with a strict getByRole("table") keep one table.
// Also the kit's shared client hook `usePrefersReducedMotion` (every recharts preset imports this
// module already).
import { useId, useState, useSyncExternalStore } from "react";
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
import { TOGGLE_ROW_CLASS } from "./layout";

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

function subscribeReducedMotion(onChange: () => void): () => void {
  if (typeof window.matchMedia !== "function") return () => {};
  const media = window.matchMedia(REDUCED_MOTION_QUERY);
  media.addEventListener("change", onChange);
  return () => media.removeEventListener("change", onChange);
}

function getReducedMotion(): boolean {
  return typeof window.matchMedia === "function" && window.matchMedia(REDUCED_MOTION_QUERY).matches;
}

/** Until the preference is known (server render, hydration) assume reduced motion. */
function getServerReducedMotion(): boolean {
  return true;
}

/**
 * `prefers-reduced-motion: reduce`, hydration-safe: the server snapshot and the hydrating client
 * render both read `true` (animation off — a reduced-motion user never gets an animated first
 * frame, and the server HTML draws the final chart), then React re-renders with the real
 * preference (and on every change). A client-only mount reads the real preference at once.
 * Used instead of `motion/react`'s `useReducedMotion`, which seeds `useState` with `null` on the
 * server and the live preference on the client, and never updates.
 */
export function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(subscribeReducedMotion, getReducedMotion, getServerReducedMotion);
}

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
    <div data-slot="chart-table-toggle" className={TOGGLE_ROW_CLASS}>
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
