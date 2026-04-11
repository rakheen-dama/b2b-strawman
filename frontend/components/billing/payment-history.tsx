import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { getPayments } from "@/app/(app)/org/[slug]/settings/billing/actions";
import { formatAmount, formatDate } from "@/lib/billing-utils";

type BadgeVariant = "success" | "destructive" | "warning" | "neutral";

function paymentStatusVariant(status: string): BadgeVariant {
  switch (status) {
    case "COMPLETE":
      return "success";
    case "FAILED":
      return "destructive";
    case "REFUNDED":
      return "warning";
    default:
      return "neutral";
  }
}

export async function PaymentHistory() {
  const response = await getPayments();
  const payments = response.content;

  if (payments.length === 0) {
    return <p className="text-sm text-slate-500 dark:text-slate-400">No payments yet.</p>;
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Date</TableHead>
          <TableHead>Amount</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>PayFast Reference</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((payment) => (
          <TableRow key={payment.id}>
            <TableCell>{formatDate(payment.paymentDate)}</TableCell>
            <TableCell>{formatAmount(payment.amountCents, payment.currency)}</TableCell>
            <TableCell>
              <Badge variant={paymentStatusVariant(payment.status)}>{payment.status}</Badge>
            </TableCell>
            <TableCell className="font-mono text-sm">{payment.payfastPaymentId}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
