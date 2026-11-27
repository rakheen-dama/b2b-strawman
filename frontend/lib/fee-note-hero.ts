import { createElement, type ReactNode } from "react";
import { AlertTriangle } from "lucide-react";
import { daysBetween } from "@b2mash/shared/date";
import type { HeroTile } from "@/components/shared/detail-hero";
import { formatCurrency, formatDate, formatDaysOverdue } from "@/lib/format";
import type { InvoiceResponse, InvoiceStatus } from "@/lib/types";

// Fee-note detail hero tiles (Phase 91 666B, ADR-396, FD-5). Pure and client-safe (no
// "server-only"): InvoiceHeaderActions calls it on useInvoiceDetail's live `invoice`, so the strip
// changes with every setInvoice (Record Payment, Approve, Send, Void, line edits) and always agrees
// with the totals block. `todayIso` (yyyy-MM-dd, South African calendar) comes from the server page
// once, so the client never computes "today" (no hydration mismatch near midnight).
// Every label already renders on the page (totals block, header "Due:"); never "Subtotal", and the
// tiles carry their own testids (fee-note-hero-tile-<id>), never invoice-balance-due /
// totals-balance-due.

export const FEE_NOTE_HERO_TILE_LABELS = {
  total: "Total",
  paid: "Amount paid",
  balance: "Balance due",
  due: "Due",
} as const;

/** Sent onwards: a payment has been or can be posted (ADR-347). */
const PAYMENT_STATUSES: ReadonlySet<InvoiceStatus> = new Set(["SENT", "PARTIALLY_PAID", "PAID"]);
/** A balance is owed (use-invoice-detail.ts: a payment can be posted while SENT / PARTIALLY_PAID). */
const BALANCE_OWED_STATUSES: ReadonlySet<InvoiceStatus> = new Set(["SENT", "PARTIALLY_PAID"]);

/** "{n}d overdue" with the --danger icon when overdue (colour is not the only signal — the text is). */
function dueHint(days: number): ReactNode {
  const text = formatDaysOverdue(days);
  if (days <= 0) return text;
  return createElement(
    "span",
    { className: "inline-flex items-center gap-1" },
    createElement(AlertTriangle, {
      "aria-hidden": true,
      className: "text-danger size-3.5 shrink-0",
    }),
    text
  );
}

/**
 * The fee-note hero strip (ADR-396): Total always; Amount paid + Balance due once sent (SENT,
 * PARTIALLY_PAID, PAID); Due for every non-draft with a due date, with a days-overdue hint only
 * while a balance is owed.
 */
export function feeNoteHeroTiles(invoice: InvoiceResponse, todayIso: string): HeroTile[] {
  const L = FEE_NOTE_HERO_TILE_LABELS;
  const { currency, status } = invoice;
  const balance = invoice.balanceDue ?? invoice.total;
  const tiles: HeroTile[] = [
    { id: "total", label: L.total, value: formatCurrency(invoice.total, currency), state: "ready" },
  ];
  if (PAYMENT_STATUSES.has(status)) {
    tiles.push(
      {
        id: "paid",
        label: L.paid,
        value: formatCurrency(invoice.amountPaid ?? 0, currency),
        state: "ready",
      },
      { id: "balance", label: L.balance, value: formatCurrency(balance, currency), state: "ready" }
    );
  }
  const dueDate = status !== "DRAFT" ? invoice.dueDate : null;
  if (dueDate) {
    const tile: HeroTile = { id: "due", label: L.due, value: formatDate(dueDate), state: "ready" };
    if (BALANCE_OWED_STATUSES.has(status) && balance > 0) {
      tile.hint = dueHint(daysBetween(dueDate.slice(0, 10), todayIso));
    }
    tiles.push(tile);
  }
  return tiles;
}
