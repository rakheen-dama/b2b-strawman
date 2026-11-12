// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): views/datatables/datatable-invoice.tsx (cells).
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
import type { ReactNode } from "react";
import { Badge } from "@b2mash/ui/badge";
import { formatCurrency, formatDate, formatLocalDate } from "@/lib/format";
import { cn } from "@/lib/utils";

/** The "no value" placeholder shared by every list table. */
export function EmptyCell() {
  return <span className="text-muted-foreground">—</span>;
}

interface MoneyCellProps {
  amount: number | null | undefined;
  /** ISO currency code; `formatCurrency` defaults to ZAR. */
  currency?: string;
  className?: string;
}

/** A currency amount in mono tabular figures (pair with `meta.align: "end"`). */
export function MoneyCell({ amount, currency, className }: MoneyCellProps) {
  if (amount === null || amount === undefined) return <EmptyCell />;
  return (
    <span className={cn("font-mono tabular-nums", className)}>
      {formatCurrency(amount, currency)}
    </span>
  );
}

interface DateCellProps {
  value: string | null | undefined;
  /** `"instant"` → `formatDate` (timestamps); `"local"` → `formatLocalDate` (`yyyy-MM-dd`). */
  kind?: "instant" | "local";
}

/** A date in the shared en-ZA format. */
export function DateCell({ value, kind = "instant" }: DateCellProps) {
  if (!value) return <EmptyCell />;
  const text = kind === "local" ? formatLocalDate(value) : formatDate(value);
  return text ? <span>{text}</span> : <EmptyCell />;
}

interface MonoCellProps {
  children: ReactNode;
  className?: string;
}

/** Reference numbers and ids: small muted mono text. */
export function MonoCell({ children, className }: MonoCellProps) {
  return (
    <span className={cn("text-muted-foreground font-mono text-xs", className)}>{children}</span>
  );
}

interface StatusBadgeCellProps {
  label: string;
  variant?: "success" | "warning" | "destructive" | "neutral" | "outline";
  /** Optional pass-through test id (e.g. a consumer's existing `matter-status-badge`). */
  "data-testid"?: string;
}

/** A status as text plus colour, never colour alone (§15.6.1). */
export function StatusBadgeCell({
  label,
  variant = "neutral",
  "data-testid": testId,
}: StatusBadgeCellProps) {
  return (
    <Badge variant={variant} data-testid={testId}>
      {label}
    </Badge>
  );
}
