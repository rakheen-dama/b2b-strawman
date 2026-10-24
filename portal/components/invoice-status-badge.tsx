import { Badge } from "@b2mash/ui/badge";
import { cn, humaniseStatus } from "@/lib/utils";

const INVOICE_STATUS_COLORS: Record<string, string> = {
  DRAFT:
    "bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300",
  APPROVED:
    "bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300",
  SENT: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  // ADR-347 (Phase 88 622B): part payment received, balance still due.
  PARTIALLY_PAID:
    "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
  PAID: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  VOID: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
};

// Phase 88 624H: sentence-case labels ("Partially paid" — ADR-347 / 624.19 copy).
const INVOICE_STATUS_LABELS: Record<string, string> = {
  DRAFT: "Draft",
  APPROVED: "Approved",
  SENT: "Sent",
  PARTIALLY_PAID: "Partially paid",
  PAID: "Paid",
  VOID: "Void",
};

interface InvoiceStatusBadgeProps {
  status: string;
  className?: string;
}

export function InvoiceStatusBadge({
  status,
  className,
}: InvoiceStatusBadgeProps) {
  const colorClasses =
    INVOICE_STATUS_COLORS[status] ?? INVOICE_STATUS_COLORS["VOID"] ?? "";

  return (
    <Badge className={cn(colorClasses, className)}>
      {INVOICE_STATUS_LABELS[status] ?? humaniseStatus(status)}
    </Badge>
  );
}
