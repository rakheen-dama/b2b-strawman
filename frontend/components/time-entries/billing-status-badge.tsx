import Link from "next/link";
import { Badge } from "@/components/ui/badge";

interface BillingStatusBadgeProps {
  billable: boolean;
  invoiceId: string | null;
  invoiceNumber: string | null;
  slug?: string;
}

export function BillingStatusBadge({
  billable,
  invoiceId,
  invoiceNumber,
  slug,
}: BillingStatusBadgeProps) {
  // Billed: invoiceId is set
  if (invoiceId) {
    const badge = <Badge variant="success">Billed</Badge>;
    if (slug) {
      return (
        <Link href={`/org/${slug}/invoices/${invoiceId}`}>{badge}</Link>
      );
    }
    return badge;
  }

  // Unbilled: billable but not yet invoiced
  if (billable) {
    return <Badge variant="neutral">Unbilled</Badge>;
  }

  // Non-billable: no badge
  return null;
}
