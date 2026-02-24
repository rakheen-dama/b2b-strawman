import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const INVOICE_STATUS_COLORS: Record<string, string> = {
  DRAFT: "bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300",
  APPROVED: "bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300",
  SENT: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  PAID: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  VOID: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
};

interface InvoiceStatusBadgeProps {
  status: string;
  className?: string;
}

function formatStatus(status: string): string {
  return status.replace(/_/g, " ");
}

export function InvoiceStatusBadge({
  status,
  className,
}: InvoiceStatusBadgeProps) {
  const colorClasses = INVOICE_STATUS_COLORS[status] ?? INVOICE_STATUS_COLORS["VOID"] ?? "";

  return (
    <Badge className={cn(colorClasses, className)}>
      {formatStatus(status)}
    </Badge>
  );
}
