"use client";

import type { MouseEvent, ReactNode } from "react";
import { useRouter } from "next/navigation";
import { TableRow } from "@/components/ui/table";

/** Clicks on these (or inside them) keep their own behaviour instead of opening the matter. */
const INTERACTIVE_SELECTOR =
  'a, button, input, [role="menu"], [role="menuitem"], [data-row-click="ignore"]';

interface MatterTableRowProps {
  href: string;
  children: ReactNode;
}

/**
 * A Matters table row: a click anywhere outside a link, button or menu opens the matter. The row
 * itself is not focusable and has no link role (that would nest interactive content and change
 * link counts); keyboard users open the matter through the Reference or Matter link.
 */
export function MatterTableRow({ href, children }: MatterTableRowProps) {
  const router = useRouter();

  function handleClick(event: MouseEvent<HTMLTableRowElement>) {
    if (event.defaultPrevented || event.button !== 0) return;
    const target = event.target as Element | null;
    // The row menu renders in a portal, but its React events still bubble to this row.
    if (target?.closest(INTERACTIVE_SELECTOR)) return;
    // Do not navigate when the user is selecting text in the row.
    if (typeof window !== "undefined" && window.getSelection()?.toString()) return;
    router.push(href);
  }

  return (
    <TableRow className="cursor-pointer" data-testid="matter-row" onClick={handleClick}>
      {children}
    </TableRow>
  );
}
