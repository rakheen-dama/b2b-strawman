"use client";

import { useRef, useState, type KeyboardEvent } from "react";

interface UseDiaryGridNavOptions {
  /** Row-major ISO dates; `null` = a cell that cannot take focus (outside the month). */
  cells: (string | null)[];
  /** Cells per row (7 for Mon–Sun). */
  columns: number;
  /** Preferred initial cell (today); falls back to the first focusable cell. */
  initial: string;
  /** Enter / Space on a focused cell. */
  onActivate?: (iso: string) => void;
}

export interface DiaryGridCellProps {
  ref: (el: HTMLElement | null) => void;
  tabIndex: number;
  onFocus: () => void;
  onKeyDown: (e: KeyboardEvent<HTMLElement>) => void;
}

/**
 * Roving tabindex for the diary's day grids (Week = 1 row, Month = rows × 7). One cell is the
 * grid's tab stop; Left / Right move ±1 day (wrapping across rows, like a calendar), Up / Down
 * ±1 row, Home / End go to the row's first / last day.
 * Moves that leave the grid or land on an out-of-month cell are ignored. Keys pressed on links or
 * menu triggers inside a cell keep their native behaviour, and those stay in the Tab order
 * (a hybrid of the APG grid pattern, so nothing is reachable by arrow keys only).
 */
export function useDiaryGridNav({ cells, columns, initial, onActivate }: UseDiaryGridNavOptions) {
  const fallback = cells.find((c): c is string => c !== null) ?? initial;
  const [active, setActive] = useState(cells.includes(initial) ? initial : fallback);
  const nodes = useRef(new Map<string, HTMLElement>());

  function move(to: string | null | undefined) {
    if (!to) return;
    setActive(to);
    nodes.current.get(to)?.focus();
  }

  function cellProps(iso: string): DiaryGridCellProps {
    const index = cells.indexOf(iso);
    const rowStart = index - (index % columns);
    const row = cells.slice(rowStart, rowStart + columns);
    return {
      ref: (el) => {
        if (el) nodes.current.set(iso, el);
        else nodes.current.delete(iso);
      },
      tabIndex: iso === active ? 0 : -1,
      onFocus: () => setActive(iso),
      onKeyDown: (e) => {
        if (e.target !== e.currentTarget) return;
        let next: string | null | undefined;
        switch (e.key) {
          case "ArrowRight":
            next = cells[index + 1];
            break;
          case "ArrowLeft":
            next = index > 0 ? cells[index - 1] : null;
            break;
          case "ArrowDown":
            next = cells[index + columns];
            break;
          case "ArrowUp":
            next = index - columns >= 0 ? cells[index - columns] : null;
            break;
          case "Home":
            next = row.find((c) => c !== null);
            break;
          case "End":
            next = [...row].reverse().find((c) => c !== null);
            break;
          case "Enter":
          case " ":
            if (onActivate) {
              e.preventDefault();
              onActivate(iso);
            }
            return;
          default:
            return;
        }
        e.preventDefault();
        move(next);
      },
    };
  }

  return { active, cellProps };
}
